package com.example.courses.enrollment.application;

import com.example.courses.catalog.domain.Course;
import com.example.courses.catalog.domain.CourseFullException;
import com.example.courses.catalog.repository.CourseRepository;
import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.enrollment.domain.EnrollmentStatus;
import com.example.courses.enrollment.domain.Student;
import com.example.courses.enrollment.repository.EnrollmentRepository;
import com.example.courses.enrollment.repository.StudentRepository;
import com.example.courses.idempotency.application.IdempotentRequests;
import com.example.courses.messaging.events.EnrollmentCompleted;
import com.example.courses.messaging.events.EnrollmentCreated;
import com.example.courses.messaging.outbox.OutboxRecorder;
import com.example.courses.payment.domain.Payment;
import com.example.courses.payment.repository.PaymentRepository;
import com.example.courses.shared.application.ResourceNotFoundException;
import com.example.courses.shared.observability.BusinessMetrics;
import com.example.courses.shared.observability.BusinessMetrics.EnrollmentRejection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
public class EnrollmentService {

    static final String ENROLL_ENDPOINT = "POST /enrollments";

    private static final Set<EnrollmentStatus> BLOCKING_STATUSES =
            EnumSet.of(EnrollmentStatus.PENDING_PAYMENT, EnrollmentStatus.ACTIVE);

    private final StudentRepository students;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final PaymentRepository payments;
    private final OutboxRecorder outbox;
    private final IdempotentRequests idempotentRequests;
    private final JsonMapper jsonMapper;
    private final BusinessMetrics metrics;
    private final EnrollmentViewMapper mapper;
    private final String currency;

    public EnrollmentService(StudentRepository students, CourseRepository courses, EnrollmentRepository enrollments,
                             PaymentRepository payments, OutboxRecorder outbox, IdempotentRequests idempotentRequests,
                             JsonMapper jsonMapper, BusinessMetrics metrics, EnrollmentViewMapper mapper,
                             @Value("${app.payments.currency:EUR}") String currency) {
        this.students = students;
        this.courses = courses;
        this.enrollments = enrollments;
        this.payments = payments;
        this.outbox = outbox;
        this.idempotentRequests = idempotentRequests;
        this.jsonMapper = jsonMapper;
        this.metrics = metrics;
        this.mapper = mapper;
        this.currency = currency;
    }

    /**
     * Reserves a seat, creates the enrollment (PENDING_PAYMENT) and its pending payment, and records
     * EnrollmentCreated, all in one transaction. With an idempotency key, a retry returns the original
     * enrollment instead of enrolling twice.
     */
    @Transactional
    public EnrollmentView enroll(UUID studentId, UUID courseId, String idempotencyKey) {
        if (idempotencyKey == null) {
            return enrollNow(studentId, courseId);
        }
        return idempotentRequests.execute(idempotencyKey, ENROLL_ENDPOINT, studentId + ":" + courseId, 201,
                () -> enrollNow(studentId, courseId),
                view -> jsonMapper.writeValueAsString(new EnrollmentReceipt(view.id())),
                body -> get(jsonMapper.readValue(body, EnrollmentReceipt.class).enrollmentId()));
    }

    @Transactional
    public EnrollmentView updateProgress(UUID enrollmentId, int progress) {
        Enrollment enrollment = findEnrollment(enrollmentId);
        if (enrollment.updateProgress(progress)) {
            outbox.record(new EnrollmentCompleted(enrollment.getId(), enrollment.getStudent().getId(),
                    enrollment.getCourse().getId(), enrollment.getCompletedAt()));
        }
        return mapper.toView(enrollment);
    }

    @Transactional
    public EnrollmentView cancel(UUID enrollmentId) {
        Enrollment enrollment = findEnrollment(enrollmentId);
        enrollment.cancel();
        EnrollmentView view = mapper.toView(enrollment);
        courses.releaseSeat(view.courseId());
        return view;
    }

    @Transactional(readOnly = true)
    public EnrollmentView get(UUID enrollmentId) {
        return mapper.toView(findEnrollment(enrollmentId));
    }

    /** Every enrollment in the platform; each null filter is ignored. */
    @Transactional(readOnly = true)
    public Page<EnrollmentSummaryView> list(UUID courseId, UUID studentId, EnrollmentStatus status,
                                            Pageable pageable) {
        return enrollments.findAll(EnrollmentRepository.matching(courseId, studentId, status), pageable)
                .map(mapper::toSummaryView);
    }

    /** A null status lists enrollments in any status. */
    @Transactional(readOnly = true)
    public Page<CourseEnrollmentView> listStudentsOfCourse(UUID courseId, EnrollmentStatus status,
                                                           Pageable pageable) {
        if (!courses.existsById(courseId)) {
            throw new ResourceNotFoundException("Course", courseId);
        }
        Page<Enrollment> page = status == null
                ? enrollments.findByCourseId(courseId, pageable)
                : enrollments.findByCourseIdAndStatus(courseId, status, pageable);
        return page.map(mapper::toCourseEnrollmentView);
    }

    /** A null status lists enrollments in any status. */
    @Transactional(readOnly = true)
    public Page<StudentEnrollmentView> listCoursesOfStudent(UUID studentId, EnrollmentStatus status,
                                                            Pageable pageable) {
        if (!students.existsById(studentId)) {
            throw new ResourceNotFoundException("Student", studentId);
        }
        Page<Enrollment> page = status == null
                ? enrollments.findByStudentId(studentId, pageable)
                : enrollments.findByStudentIdAndStatus(studentId, status, pageable);
        return page.map(mapper::toStudentEnrollmentView);
    }

    private EnrollmentView enrollNow(UUID studentId, UUID courseId) {
        try {
            EnrollmentView enrollment = reserveSeatAndEnroll(studentId, courseId);
            metrics.enrollmentCreated();
            return enrollment;
        } catch (CourseFullException e) {
            metrics.enrollmentRejected(EnrollmentRejection.COURSE_FULL);
            throw e;
        } catch (AlreadyEnrolledException e) {
            metrics.enrollmentRejected(EnrollmentRejection.ALREADY_ENROLLED);
            throw e;
        }
    }

    private EnrollmentView reserveSeatAndEnroll(UUID studentId, UUID courseId) {
        if (enrollments.existsByCourseIdAndStudentIdAndStatusIn(courseId, studentId, BLOCKING_STATUSES)) {
            throw new AlreadyEnrolledException(studentId, courseId);
        }
        if (courses.tryReserveSeat(courseId) == 0) {
            Course course = courses.findById(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
            course.assertAcceptsEnrollment();
            // A seat was freed between the UPDATE and this read; report full rather than retrying.
            throw new CourseFullException(courseId);
        }
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        Course course = courses.findById(courseId).orElseThrow();

        Enrollment enrollment = saveNewEnrollment(Enrollment.requestFor(student, course), studentId, courseId);
        Payment payment = payments.save(Payment.requestFor(
                enrollment, course.getPrice(), currency, "enrollment-" + enrollment.getId()));
        outbox.record(new EnrollmentCreated(enrollment.getId(), studentId, courseId, payment.getId(),
                payment.getAmount(), payment.getCurrency(), Instant.now()));
        return mapper.toView(enrollment);
    }

    /** The partial unique index is the backstop for two concurrent enrollments of the same student. */
    private Enrollment saveNewEnrollment(Enrollment enrollment, UUID studentId, UUID courseId) {
        try {
            return enrollments.saveAndFlush(enrollment);
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyEnrolledException(studentId, courseId);
        }
    }

    private Enrollment findEnrollment(UUID enrollmentId) {
        return enrollments.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));
    }

    private record EnrollmentReceipt(UUID enrollmentId) {
    }
}

package io.github.dreyes17.courses.enrollment.mcp;

import io.github.dreyes17.courses.enrollment.application.CourseEnrollmentView;
import io.github.dreyes17.courses.enrollment.application.EnrollmentService;
import io.github.dreyes17.courses.enrollment.application.EnrollmentSummaryView;
import io.github.dreyes17.courses.enrollment.application.EnrollmentView;
import io.github.dreyes17.courses.enrollment.application.StudentEnrollmentView;
import io.github.dreyes17.courses.enrollment.application.StudentService;
import io.github.dreyes17.courses.enrollment.application.StudentView;
import io.github.dreyes17.courses.enrollment.domain.EnrollmentStatus;
import io.github.dreyes17.courses.shared.mcp.McpPaging;
import io.github.dreyes17.courses.shared.security.CurrentUser;
import io.github.dreyes17.courses.shared.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

/**
 * MCP adapter for students and enrollments, like the REST controllers in {@code enrollment.web}: same input
 * validation, same authorization rule per operation, same use cases.
 */
@Component
@Validated
class EnrollmentTools {

    private static final String STATUS_FILTER_DESCRIPTION =
            "Only enrollments in this status: PENDING_PAYMENT, ACTIVE, COMPLETED or CANCELLED";

    private final EnrollmentService enrollments;
    private final StudentService students;

    EnrollmentTools(EnrollmentService enrollments, StudentService students) {
        this.enrollments = enrollments;
        this.students = students;
    }

    @McpTool(name = "list_students", description = """
            List registered students, oldest first. The filters are optional and combine with AND. ADMIN only.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<StudentView> listStudents(
            @McpToolParam(description = "Case-insensitive text contained in the first or last name", required = false)
            String name,
            @McpToolParam(description = "Case-insensitive text contained in the email", required = false)
            String email,
            @McpToolParam(description = McpPaging.PAGE_DESCRIPTION, required = false) Integer page,
            @McpToolParam(description = McpPaging.SIZE_DESCRIPTION, required = false) Integer size) {
        return PageResponse.from(students.list(name, email, McpPaging.page(page, size, Sort.by("createdAt"))));
    }

    @McpTool(name = "get_student", description = "Get one student by id. ADMIN or the student themselves.",
            annotations = @McpAnnotations(readOnlyHint = true))
    @PreAuthorize("hasRole('ADMIN') or @access.isStudent(authentication, #studentId)")
    public StudentView getStudent(@McpToolParam(description = "Student id (UUID)") @NotNull UUID studentId) {
        return students.get(studentId);
    }

    @McpTool(name = "enroll_student", description = """
            Enroll the authenticated student in a PUBLISHED course. Atomically reserves a seat and creates a \
            pending payment: the enrollment comes back as PENDING_PAYMENT and becomes ACTIVE once the payment is \
            confirmed asynchronously (check it with get_enrollment). Fails if the course is full or the student \
            is already enrolled. Calling it again with the same idempotencyKey returns the original enrollment \
            instead of enrolling twice, so it is safe to retry. STUDENT only.""",
            annotations = @McpAnnotations(destructiveHint = false, idempotentHint = true))
    @PreAuthorize("hasRole('STUDENT')")
    public EnrollmentView enrollStudent(
            @McpToolParam(description = "Course id (UUID)") @NotNull UUID courseId,
            @McpToolParam(description = """
                    Unique key for this enrollment attempt, e.g. a new UUID. Reuse it only to retry the same \
                    attempt.""") @NotBlank @Size(max = 100) String idempotencyKey) {
        UUID studentId = CurrentUser.from(SecurityContextHolder.getContext().getAuthentication()).studentId();
        return enrollments.enroll(studentId, courseId, idempotencyKey);
    }

    @McpTool(name = "list_enrollments", description = """
            List every enrollment in the platform, newest first, with the student's name and email and the course \
            title. The filters are optional and combine with AND. ADMIN only.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<EnrollmentSummaryView> listEnrollments(
            @McpToolParam(description = "Only enrollments in this course (UUID)", required = false) UUID courseId,
            @McpToolParam(description = "Only enrollments of this student (UUID)", required = false) UUID studentId,
            @McpToolParam(description = STATUS_FILTER_DESCRIPTION, required = false) EnrollmentStatus status,
            @McpToolParam(description = McpPaging.PAGE_DESCRIPTION, required = false) Integer page,
            @McpToolParam(description = McpPaging.SIZE_DESCRIPTION, required = false) Integer size) {
        return PageResponse.from(enrollments.list(courseId, studentId, status,
                McpPaging.page(page, size, Sort.by(Sort.Direction.DESC, "enrolledAt"))));
    }

    @McpTool(name = "get_enrollment", description = """
            Get one enrollment with its status (PENDING_PAYMENT, ACTIVE, COMPLETED, CANCELLED) and progress. \
            ADMIN, its student, or the course's instructor.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    @PreAuthorize("hasRole('ADMIN') or @access.canViewEnrollment(authentication, #enrollmentId)")
    public EnrollmentView getEnrollment(
            @McpToolParam(description = "Enrollment id (UUID)") @NotNull UUID enrollmentId) {
        return enrollments.get(enrollmentId);
    }

    @McpTool(name = "update_enrollment_progress", description = """
            Set the progress (0-100) of an ACTIVE enrollment; it can't go backwards. Reaching 100 completes the \
            enrollment and the certificate is issued asynchronously. ADMIN or its student.""",
            annotations = @McpAnnotations(destructiveHint = false, idempotentHint = true))
    @PreAuthorize("hasRole('ADMIN') or @access.ownsEnrollment(authentication, #enrollmentId)")
    public EnrollmentView updateEnrollmentProgress(
            @McpToolParam(description = "Enrollment id (UUID)") @NotNull UUID enrollmentId,
            @McpToolParam(description = "New progress, 0 to 100") @NotNull @Min(0) @Max(100) Integer progress) {
        return enrollments.updateProgress(enrollmentId, progress);
    }

    @McpTool(name = "cancel_enrollment", description = """
            Cancel a PENDING_PAYMENT or ACTIVE enrollment and release its seat. ADMIN or its student.""")
    @PreAuthorize("hasRole('ADMIN') or @access.ownsEnrollment(authentication, #enrollmentId)")
    public EnrollmentView cancelEnrollment(
            @McpToolParam(description = "Enrollment id (UUID)") @NotNull UUID enrollmentId) {
        return enrollments.cancel(enrollmentId);
    }

    @McpTool(name = "list_students_by_course", description = """
            List the students enrolled in a course, in enrollment order, with each enrollment's status and \
            progress. ADMIN or the course's instructor.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    @PreAuthorize("hasRole('ADMIN') or @access.teachesCourse(authentication, #courseId)")
    public PageResponse<CourseEnrollmentView> listStudentsByCourse(
            @McpToolParam(description = "Course id (UUID)") @NotNull UUID courseId,
            @McpToolParam(description = STATUS_FILTER_DESCRIPTION, required = false) EnrollmentStatus status,
            @McpToolParam(description = McpPaging.PAGE_DESCRIPTION, required = false) Integer page,
            @McpToolParam(description = McpPaging.SIZE_DESCRIPTION, required = false) Integer size) {
        return PageResponse.from(enrollments.listStudentsOfCourse(courseId, status,
                McpPaging.page(page, size, Sort.by("enrolledAt"))));
    }

    @McpTool(name = "list_courses_by_student", description = """
            List the courses a student is enrolled in, in enrollment order, with status and progress. ADMIN or \
            the student themselves.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    @PreAuthorize("hasRole('ADMIN') or @access.isStudent(authentication, #studentId)")
    public PageResponse<StudentEnrollmentView> listCoursesByStudent(
            @McpToolParam(description = "Student id (UUID)") @NotNull UUID studentId,
            @McpToolParam(description = STATUS_FILTER_DESCRIPTION, required = false) EnrollmentStatus status,
            @McpToolParam(description = McpPaging.PAGE_DESCRIPTION, required = false) Integer page,
            @McpToolParam(description = McpPaging.SIZE_DESCRIPTION, required = false) Integer size) {
        return PageResponse.from(enrollments.listCoursesOfStudent(studentId, status,
                McpPaging.page(page, size, Sort.by("enrolledAt"))));
    }
}

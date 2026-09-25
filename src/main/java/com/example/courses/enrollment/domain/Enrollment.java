package com.example.courses.enrollment.domain;

import com.example.courses.catalog.domain.Course;
import com.example.courses.shared.domain.BaseEntity;
import com.example.courses.shared.domain.BusinessRuleViolationException;
import com.example.courses.shared.domain.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "enrollments")
public class Enrollment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status;

    @Column(nullable = false)
    private int progress;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Enrollment() {
    }

    private Enrollment(Student student, Course course) {
        this.student = Objects.requireNonNull(student, "student");
        this.course = Objects.requireNonNull(course, "course");
        this.status = EnrollmentStatus.PENDING_PAYMENT;
        this.progress = 0;
        this.enrolledAt = Instant.now();
    }

    public static Enrollment requestFor(Student student, Course course) {
        return new Enrollment(student, course);
    }

    public void activate() {
        if (status != EnrollmentStatus.PENDING_PAYMENT) {
            throw new InvalidStateTransitionException("Enrollment", getId(), status, "be activated");
        }
        status = EnrollmentStatus.ACTIVE;
    }

    public boolean updateProgress(int newProgress) {
        if (status != EnrollmentStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Enrollment", getId(), status, "update progress");
        }
        if (newProgress < 0 || newProgress > 100) {
            throw new BusinessRuleViolationException("progress must be between 0 and 100, got " + newProgress);
        }
        if (newProgress < progress) {
            throw new BusinessRuleViolationException(
                    "progress cannot go backwards from %d to %d".formatted(progress, newProgress));
        }
        progress = newProgress;
        if (progress == 100) {
            status = EnrollmentStatus.COMPLETED;
            completedAt = Instant.now();
            return true;
        }
        return false;
    }

    public void cancel() {
        Set<EnrollmentStatus> cancellable = Set.of(EnrollmentStatus.PENDING_PAYMENT, EnrollmentStatus.ACTIVE);
        if (!cancellable.contains(status)) {
            throw new InvalidStateTransitionException("Enrollment", getId(), status, "be cancelled");
        }
        status = EnrollmentStatus.CANCELLED;
    }

    public Student getStudent() {
        return student;
    }

    public Course getCourse() {
        return course;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version;
    }
}

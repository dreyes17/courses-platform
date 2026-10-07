package io.github.dreyes17.courses.shared.security;

import io.github.dreyes17.courses.catalog.repository.CourseRepository;
import io.github.dreyes17.courses.enrollment.repository.EnrollmentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ownership checks referenced from {@code @PreAuthorize} as {@code @access}. An unknown id yields false
 * (403), so callers without access can't probe which resources exist.
 */
@Component("access")
public class AccessRules {

    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;

    public AccessRules(EnrollmentRepository enrollments, CourseRepository courses) {
        this.enrollments = enrollments;
        this.courses = courses;
    }

    public boolean isStudent(Authentication authentication, UUID studentId) {
        CurrentUser user = CurrentUser.from(authentication);
        return user != null && studentId != null && studentId.equals(user.studentId());
    }

    public boolean isInstructor(Authentication authentication, UUID instructorId) {
        CurrentUser user = CurrentUser.from(authentication);
        return user != null && instructorId != null && instructorId.equals(user.instructorId());
    }

    public boolean ownsEnrollment(Authentication authentication, UUID enrollmentId) {
        CurrentUser user = CurrentUser.from(authentication);
        return user != null && user.studentId() != null
                && enrollments.existsByIdAndStudentId(enrollmentId, user.studentId());
    }

    public boolean teachesCourse(Authentication authentication, UUID courseId) {
        CurrentUser user = CurrentUser.from(authentication);
        return user != null && user.instructorId() != null
                && courses.existsByIdAndInstructorId(courseId, user.instructorId());
    }

    public boolean canViewEnrollment(Authentication authentication, UUID enrollmentId) {
        CurrentUser user = CurrentUser.from(authentication);
        if (user == null) {
            return false;
        }
        return ownsEnrollment(authentication, enrollmentId)
                || (user.instructorId() != null
                    && enrollments.existsByIdAndCourseInstructorId(enrollmentId, user.instructorId()));
    }
}

package com.example.courses.catalog.application;

import com.example.courses.catalog.domain.CourseStatus;

import java.util.UUID;

/**
 * Which courses a caller can see: an ADMIN sees every course; an INSTRUCTOR sees the PUBLISHED ones plus every
 * course they teach, whatever its status; anyone else only sees PUBLISHED courses. A course outside this scope
 * is reported as not found, so its existence isn't revealed.
 *
 * @param everything   true for an ADMIN
 * @param instructorId the instructor whose own courses are visible in any status, or null
 */
public record CourseVisibility(boolean everything, UUID instructorId) {

    public static final CourseVisibility ALL = new CourseVisibility(true, null);
    public static final CourseVisibility PUBLISHED_ONLY = new CourseVisibility(false, null);

    public static CourseVisibility of(boolean admin, UUID instructorId) {
        return admin ? ALL : new CourseVisibility(false, instructorId);
    }

    public boolean allows(CourseView course) {
        return everything || course.status() == CourseStatus.PUBLISHED
                || (instructorId != null && instructorId.equals(course.instructorId()));
    }
}

package com.example.courses.enrollment.repository;

import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.enrollment.domain.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    /** Student is to-one, so the fetch keeps pagination in SQL and loads each page in one query. */
    @EntityGraph(attributePaths = "student")
    Page<Enrollment> findByCourseId(UUID courseId, Pageable pageable);

    @EntityGraph(attributePaths = "student")
    Page<Enrollment> findByCourseIdAndStatus(UUID courseId, EnrollmentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "course")
    Page<Enrollment> findByStudentId(UUID studentId, Pageable pageable);

    @EntityGraph(attributePaths = "course")
    Page<Enrollment> findByStudentIdAndStatus(UUID studentId, EnrollmentStatus status, Pageable pageable);

    boolean existsByIdAndStudentId(UUID id, UUID studentId);

    boolean existsByIdAndCourseInstructorId(UUID id, UUID instructorId);

    boolean existsByCourseIdAndStudentIdAndStatusIn(UUID courseId, UUID studentId,
                                                      Collection<EnrollmentStatus> statuses);
}

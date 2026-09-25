package com.example.courses.enrollment.repository;

import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.enrollment.domain.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    @Query("select e from Enrollment e join fetch e.student where e.course.id = :courseId")
    List<Enrollment> findByCourseIdFetchStudent(@Param("courseId") UUID courseId);

    @Query("select e from Enrollment e join fetch e.course where e.student.id = :studentId")
    List<Enrollment> findByStudentIdFetchCourse(@Param("studentId") UUID studentId);

    Optional<Enrollment> findByIdAndStudentId(UUID id, UUID studentId);

    boolean existsByCourseIdAndStudentIdAndStatusIn(UUID courseId, UUID studentId,
                                                      Collection<EnrollmentStatus> statuses);
}

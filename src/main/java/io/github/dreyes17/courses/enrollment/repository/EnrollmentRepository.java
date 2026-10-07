package io.github.dreyes17.courses.enrollment.repository;

import io.github.dreyes17.courses.enrollment.domain.Enrollment;
import io.github.dreyes17.courses.enrollment.domain.EnrollmentStatus;
import io.github.dreyes17.courses.shared.repository.SpecificationFilters;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID>, JpaSpecificationExecutor<Enrollment> {

    /** Null filters are ignored, so any combination can be sent. */
    static Specification<Enrollment> matching(UUID courseId, UUID studentId, EnrollmentStatus status) {
        return SpecificationFilters.allOf(
                courseId == null ? null : (root, query, cb) -> cb.equal(root.get("course").get("id"), courseId),
                studentId == null ? null : (root, query, cb) -> cb.equal(root.get("student").get("id"), studentId),
                SpecificationFilters.equalTo("status", status));
    }

    /** Student and course are to-one, so fetching both keeps SQL-level pagination and avoids N+1. */
    @Override
    @EntityGraph(attributePaths = {"student", "course"})
    Page<Enrollment> findAll(Specification<Enrollment> specification, Pageable pageable);

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

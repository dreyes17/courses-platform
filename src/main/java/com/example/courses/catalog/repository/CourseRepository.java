package com.example.courses.catalog.repository;

import com.example.courses.catalog.domain.Course;
import com.example.courses.shared.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {

    /** Category and instructor are to-one, so fetching them keeps SQL-level pagination and avoids N+1. */
    @Override
    @EntityGraph(attributePaths = {"category", "instructor"})
    Page<Course> findAll(Specification<Course> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "instructor"})
    Optional<Course> findWithDetailsById(UUID id);

    boolean existsByIdAndInstructorId(UUID id, UUID instructorId);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByInstructorId(UUID instructorId);

    /**
     * Returns 1 if a seat was reserved, 0 otherwise. Bumping {@code version} makes a concurrent
     * optimistic-locked edit of the same course fail instead of overwriting seatsTaken with a stale value.
     * Evicts the cached course view (after commit), since its availableSeats is now out of date.
     */
    @CacheEvict(cacheNames = CacheConfig.COURSES, key = "#p0")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Course c
               set c.seatsTaken = c.seatsTaken + 1, c.version = c.version + 1
             where c.id = :courseId
               and c.status = com.example.courses.catalog.domain.CourseStatus.PUBLISHED
               and c.seatsTaken < c.capacity
            """)
    int tryReserveSeat(@Param("courseId") UUID courseId);

    /** Evicts the cached course view (after commit), since its availableSeats is now out of date. */
    @CacheEvict(cacheNames = CacheConfig.COURSES, key = "#p0")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Course c
               set c.seatsTaken = c.seatsTaken - 1, c.version = c.version + 1
             where c.id = :courseId
               and c.seatsTaken > 0
            """)
    int releaseSeat(@Param("courseId") UUID courseId);
}

package com.example.courses.catalog.repository;

import com.example.courses.catalog.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {

    /**
     * Returns 1 if a seat was reserved, 0 otherwise. Bumping {@code version} makes a concurrent
     * optimistic-locked edit of the same course fail instead of overwriting seatsTaken with a stale value.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Course c
               set c.seatsTaken = c.seatsTaken + 1, c.version = c.version + 1
             where c.id = :courseId
               and c.status = com.example.courses.catalog.domain.CourseStatus.PUBLISHED
               and c.seatsTaken < c.capacity
            """)
    int tryReserveSeat(@Param("courseId") UUID courseId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Course c
               set c.seatsTaken = c.seatsTaken - 1, c.version = c.version + 1
             where c.id = :courseId
               and c.seatsTaken > 0
            """)
    int releaseSeat(@Param("courseId") UUID courseId);
}

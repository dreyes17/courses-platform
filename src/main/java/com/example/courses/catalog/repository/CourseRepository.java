package com.example.courses.catalog.repository;

import com.example.courses.catalog.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * {@link JpaSpecificationExecutor} backs the combinable catalog search (category, level, price
 * range, title, availability) added once the search endpoint is implemented.
 */
public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {
}

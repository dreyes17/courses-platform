package io.github.dreyes17.courses.catalog.application;

import io.github.dreyes17.courses.catalog.repository.CourseRepository;
import io.github.dreyes17.courses.shared.application.ResourceNotFoundException;
import io.github.dreyes17.courses.shared.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Course views by id, whatever their status. A separate bean from CourseService for two reasons: the
 * visibility check must run on every call, cache hits included (a cached draft must stay hidden from students
 * and other instructors), and Spring's cache proxy doesn't intercept a bean's calls to itself.
 * <p>
 * Evicted by CourseService on every course edit, and by CourseRepository whenever a seat is reserved or
 * released, so availableSeats never lags behind an enrollment.
 */
@Component
class CourseViewCache {

    private final CourseRepository courses;
    private final CatalogViewMapper mapper;

    CourseViewCache(CourseRepository courses, CatalogViewMapper mapper) {
        this.courses = courses;
        this.mapper = mapper;
    }

    @Cacheable(cacheNames = CacheConfig.COURSES, key = "#id")
    @Transactional(readOnly = true)
    public CourseView get(UUID id) {
        return courses.findWithDetailsById(id).map(mapper::toView)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }
}

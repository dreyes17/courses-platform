package com.example.courses.catalog.application;

import com.example.courses.catalog.domain.Category;
import com.example.courses.catalog.domain.Course;
import com.example.courses.catalog.domain.CourseStatus;
import com.example.courses.catalog.domain.Instructor;
import com.example.courses.catalog.repository.CategoryRepository;
import com.example.courses.catalog.repository.CourseRepository;
import com.example.courses.catalog.repository.CourseSpecifications;
import com.example.courses.catalog.repository.InstructorRepository;
import com.example.courses.shared.application.CursorPage;
import com.example.courses.shared.application.ResourceNotFoundException;
import com.example.courses.shared.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CourseService {

    /** Total order for keyset pagination; must match CourseSpecifications.createdBefore. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final CourseRepository courses;
    private final CategoryRepository categories;
    private final InstructorRepository instructors;
    private final CatalogViewMapper mapper;
    private final CourseViewCache courseViews;

    public CourseService(CourseRepository courses, CategoryRepository categories, InstructorRepository instructors,
                         CatalogViewMapper mapper, CourseViewCache courseViews) {
        this.courses = courses;
        this.categories = categories;
        this.instructors = instructors;
        this.mapper = mapper;
        this.courseViews = courseViews;
    }

    @Transactional
    public CourseView createDraft(CourseTerms terms, UUID categoryId, UUID instructorId) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        Instructor instructor = instructors.findById(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor", instructorId));
        Course course = Course.draft(terms.title(), terms.description(), terms.durationHours(), terms.level(),
                terms.price(), terms.capacity(), category, instructor);
        return mapper.toView(courses.save(course));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.COURSES, key = "#id")
    public CourseView update(UUID id, CourseTerms terms) {
        Course course = find(id);
        course.updateDetails(terms.title(), terms.description(), terms.durationHours(), terms.level(),
                terms.price(), terms.capacity());
        return mapper.toView(course);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.COURSES, key = "#id")
    public CourseView publish(UUID id) {
        Course course = find(id);
        course.publish();
        return mapper.toView(course);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.COURSES, key = "#id")
    public CourseView archive(UUID id) {
        Course course = find(id);
        course.archive();
        return mapper.toView(course);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.COURSES, key = "#id")
    public void delete(UUID id) {
        Course course = find(id);
        course.assertDeletable();
        courses.delete(course);
    }

    /** With publishedOnly, drafts and archived courses are reported as not found. Served from the cache. */
    public CourseView get(UUID id, boolean publishedOnly) {
        CourseView course = courseViews.get(id);
        if (publishedOnly && course.status() != CourseStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Course", id);
        }
        return course;
    }

    @Transactional(readOnly = true)
    public Page<CourseView> search(CourseSearchCriteria criteria, Pageable pageable, boolean publishedOnly) {
        CourseStatus status = publishedOnly ? CourseStatus.PUBLISHED : criteria.status();
        var specification = CourseSpecifications.matching(criteria.categoryId(), criteria.level(),
                criteria.minPrice(), criteria.maxPrice(), criteria.title(), criteria.withAvailableSeats(),
                status);
        return courses.findAll(specification, pageable).map(mapper::toView);
    }

    /**
     * Keyset ("cursor") pagination over the same filters as {@link #search}, newest first. The cursor marks the
     * last course returned, so courses created or deleted between two calls never shift the next page into
     * duplicates or gaps, and a deep page costs the same as the first one. {@code size + 1} rows are read to
     * know whether there is a next page without a count query.
     */
    @Transactional(readOnly = true)
    public CursorPage<CourseView> scroll(CourseSearchCriteria criteria, String cursor, int size,
                                         boolean publishedOnly) {
        CourseStatus status = publishedOnly ? CourseStatus.PUBLISHED : criteria.status();
        Specification<Course> specification = CourseSpecifications.matching(criteria.categoryId(), criteria.level(),
                        criteria.minPrice(), criteria.maxPrice(), criteria.title(), criteria.withAvailableSeats(),
                        status)
                .and(CourseSpecifications.fetchingCategoryAndInstructor());
        if (cursor != null) {
            CourseCursor after = CourseCursor.decode(cursor);
            specification = specification.and(CourseSpecifications.createdBefore(after.createdAt(), after.id()));
        }
        List<Course> rows = courses.findBy(specification, query -> query.sortBy(NEWEST_FIRST).limit(size + 1).all());
        boolean hasMore = rows.size() > size;
        List<Course> page = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = hasMore ? CourseCursor.after(page.getLast()).encode() : null;
        return new CursorPage<>(page.stream().map(mapper::toView).toList(), nextCursor);
    }

    private Course find(UUID id) {
        return courses.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }
}

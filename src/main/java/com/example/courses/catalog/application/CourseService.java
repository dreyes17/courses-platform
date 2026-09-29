package com.example.courses.catalog.application;

import com.example.courses.catalog.domain.Category;
import com.example.courses.catalog.domain.Course;
import com.example.courses.catalog.domain.CourseStatus;
import com.example.courses.catalog.domain.Instructor;
import com.example.courses.catalog.repository.CategoryRepository;
import com.example.courses.catalog.repository.CourseRepository;
import com.example.courses.catalog.repository.CourseSpecifications;
import com.example.courses.catalog.repository.InstructorRepository;
import com.example.courses.shared.application.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CourseService {

    private final CourseRepository courses;
    private final CategoryRepository categories;
    private final InstructorRepository instructors;
    private final CatalogViewMapper mapper;

    public CourseService(CourseRepository courses, CategoryRepository categories, InstructorRepository instructors,
                         CatalogViewMapper mapper) {
        this.courses = courses;
        this.categories = categories;
        this.instructors = instructors;
        this.mapper = mapper;
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
    public CourseView update(UUID id, CourseTerms terms) {
        Course course = find(id);
        course.updateDetails(terms.title(), terms.description(), terms.durationHours(), terms.level(),
                terms.price(), terms.capacity());
        return mapper.toView(course);
    }

    @Transactional
    public CourseView publish(UUID id) {
        Course course = find(id);
        course.publish();
        return mapper.toView(course);
    }

    @Transactional
    public CourseView archive(UUID id) {
        Course course = find(id);
        course.archive();
        return mapper.toView(course);
    }

    @Transactional
    public void delete(UUID id) {
        Course course = find(id);
        course.assertDeletable();
        courses.delete(course);
    }

    /** With publishedOnly, drafts and archived courses are reported as not found. */
    @Transactional(readOnly = true)
    public CourseView get(UUID id, boolean publishedOnly) {
        Course course = find(id);
        if (publishedOnly && course.getStatus() != CourseStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Course", id);
        }
        return mapper.toView(course);
    }

    @Transactional(readOnly = true)
    public Page<CourseView> search(CourseSearchCriteria criteria, Pageable pageable, boolean publishedOnly) {
        CourseStatus status = publishedOnly ? CourseStatus.PUBLISHED : criteria.status();
        var specification = CourseSpecifications.matching(criteria.categoryId(), criteria.level(),
                criteria.minPrice(), criteria.maxPrice(), criteria.title(), criteria.withAvailableSeats(),
                status);
        return courses.findAll(specification, pageable).map(mapper::toView);
    }

    private Course find(UUID id) {
        return courses.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }
}

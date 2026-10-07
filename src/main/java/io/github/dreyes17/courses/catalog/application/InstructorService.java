package io.github.dreyes17.courses.catalog.application;

import io.github.dreyes17.courses.catalog.domain.Instructor;
import io.github.dreyes17.courses.catalog.repository.CourseRepository;
import io.github.dreyes17.courses.catalog.repository.InstructorRepository;
import io.github.dreyes17.courses.shared.application.DuplicateResourceException;
import io.github.dreyes17.courses.shared.application.ResourceInUseException;
import io.github.dreyes17.courses.shared.application.ResourceNotFoundException;
import io.github.dreyes17.courses.shared.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InstructorService {

    private final InstructorRepository instructors;
    private final CourseRepository courses;
    private final CatalogViewMapper mapper;

    public InstructorService(InstructorRepository instructors, CourseRepository courses, CatalogViewMapper mapper) {
        this.instructors = instructors;
        this.courses = courses;
        this.mapper = mapper;
    }

    @Transactional
    public InstructorView create(String name, String email, String bio) {
        if (instructors.existsByEmail(email)) {
            throw new DuplicateResourceException("Instructor", "email", email);
        }
        return mapper.toView(instructors.save(Instructor.create(name, email, bio)));
    }

    /** Course views embed the instructor name, so they are evicted too. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.INSTRUCTORS, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.COURSES, allEntries = true)})
    public InstructorView updateProfile(UUID id, String name, String bio) {
        Instructor instructor = find(id);
        instructor.updateProfile(name, bio);
        return mapper.toView(instructor);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.INSTRUCTORS, key = "#id")
    public void delete(UUID id) {
        Instructor instructor = find(id);
        if (courses.existsByInstructorId(id)) {
            throw new ResourceInUseException("Instructor", id, "they still have courses");
        }
        instructors.delete(instructor);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.INSTRUCTORS, key = "#id")
    public InstructorView get(UUID id) {
        return mapper.toView(find(id));
    }

    @Transactional(readOnly = true)
    public Page<InstructorView> list(String nameContains, String emailContains, Pageable pageable) {
        return instructors.findAll(InstructorRepository.matching(nameContains, emailContains), pageable)
                .map(mapper::toView);
    }

    private Instructor find(UUID id) {
        return instructors.findById(id).orElseThrow(() -> new ResourceNotFoundException("Instructor", id));
    }
}

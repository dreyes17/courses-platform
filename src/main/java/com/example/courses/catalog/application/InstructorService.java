package com.example.courses.catalog.application;

import com.example.courses.catalog.domain.Instructor;
import com.example.courses.catalog.repository.CourseRepository;
import com.example.courses.catalog.repository.InstructorRepository;
import com.example.courses.shared.application.DuplicateResourceException;
import com.example.courses.shared.application.ResourceInUseException;
import com.example.courses.shared.application.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InstructorService {

    private final InstructorRepository instructors;
    private final CourseRepository courses;

    public InstructorService(InstructorRepository instructors, CourseRepository courses) {
        this.instructors = instructors;
        this.courses = courses;
    }

    @Transactional
    public InstructorView create(String name, String email, String bio) {
        if (instructors.existsByEmail(email)) {
            throw new DuplicateResourceException("Instructor", "email", email);
        }
        return InstructorView.from(instructors.save(Instructor.create(name, email, bio)));
    }

    @Transactional
    public InstructorView updateProfile(UUID id, String name, String bio) {
        Instructor instructor = find(id);
        instructor.updateProfile(name, bio);
        return InstructorView.from(instructor);
    }

    @Transactional
    public void delete(UUID id) {
        Instructor instructor = find(id);
        if (courses.existsByInstructorId(id)) {
            throw new ResourceInUseException("Instructor", id, "they still have courses");
        }
        instructors.delete(instructor);
    }

    @Transactional(readOnly = true)
    public InstructorView get(UUID id) {
        return InstructorView.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<InstructorView> list(Pageable pageable) {
        return instructors.findAll(pageable).map(InstructorView::from);
    }

    private Instructor find(UUID id) {
        return instructors.findById(id).orElseThrow(() -> new ResourceNotFoundException("Instructor", id));
    }
}

package com.example.courses.enrollment.application;

import com.example.courses.enrollment.domain.Student;
import com.example.courses.enrollment.repository.StudentRepository;
import com.example.courses.shared.application.DuplicateResourceException;
import com.example.courses.shared.application.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StudentService {

    private final StudentRepository students;

    public StudentService(StudentRepository students) {
        this.students = students;
    }

    @Transactional
    public StudentView register(String firstName, String lastName, String email) {
        if (students.existsByEmail(email)) {
            throw new DuplicateResourceException("Student", "email", email);
        }
        return StudentView.from(students.save(Student.register(firstName, lastName, email)));
    }

    @Transactional(readOnly = true)
    public StudentView get(UUID id) {
        return StudentView.from(students.findById(id).orElseThrow(() -> new ResourceNotFoundException("Student", id)));
    }

    @Transactional(readOnly = true)
    public Page<StudentView> list(Pageable pageable) {
        return students.findAll(pageable).map(StudentView::from);
    }
}

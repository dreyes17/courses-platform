package com.example.courses.enrollment.web;

import com.example.courses.enrollment.application.StudentService;
import com.example.courses.enrollment.application.StudentView;
import com.example.courses.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Students sign up through POST /api/auth/register, which also creates their login. */
@RestController
@RequestMapping("/api/students")
@Tag(name = "Students")
class StudentController {

    private final StudentService students;

    StudentController(StudentService students) {
        this.students = students;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List students. ADMIN only",
            description = "Optional filters, combinable: name (first or last name) and email, both "
                    + "case-insensitive substrings.")
    PageResponse<StudentView> list(@RequestParam(required = false) String name,
                                   @RequestParam(required = false) String email,
                                   @ParameterObject @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return PageResponse.from(students.list(name, email, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @access.isStudent(authentication, #id)")
    @Operation(summary = "Get a student. ADMIN or the student themselves")
    StudentView get(@PathVariable UUID id) {
        return students.get(id);
    }
}

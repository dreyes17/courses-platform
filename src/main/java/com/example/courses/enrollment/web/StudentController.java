package com.example.courses.enrollment.web;

import com.example.courses.enrollment.application.StudentService;
import com.example.courses.enrollment.application.StudentView;
import com.example.courses.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/students")
@Tag(name = "Students")
class StudentController {

    private final StudentService students;

    StudentController(StudentService students) {
        this.students = students;
    }

    @PostMapping
    @Operation(summary = "Register a student (email must be unique)")
    ResponseEntity<StudentView> register(@Valid @RequestBody RegisterStudentRequest request) {
        StudentView created = students.register(request.firstName(), request.lastName(), request.email());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id());
        return ResponseEntity.created(location.toUri()).body(created);
    }

    @GetMapping
    @Operation(summary = "List students")
    PageResponse<StudentView> list(@ParameterObject @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return PageResponse.from(students.list(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a student")
    StudentView get(@PathVariable UUID id) {
        return students.get(id);
    }

    record RegisterStudentRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email) {
    }
}

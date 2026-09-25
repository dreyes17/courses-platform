package com.example.courses.identity.web;

import com.example.courses.enrollment.application.StudentView;
import com.example.courses.identity.application.AccessToken;
import com.example.courses.identity.application.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
@SecurityRequirements
class AuthController {

    private final AccountService accounts;

    AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/register")
    @Operation(summary = "Sign up as a student (public)")
    ResponseEntity<StudentView> register(@Valid @RequestBody RegisterRequest request) {
        StudentView student = accounts.registerStudent(request.firstName(), request.lastName(), request.email(),
                request.password());
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/students/{id}").buildAndExpand(student.id());
        return ResponseEntity.created(location.toUri()).body(student);
    }

    @PostMapping("/token")
    @Operation(summary = "Exchange email and password for a bearer token (public)")
    AccessToken token(@Valid @RequestBody TokenRequest request) {
        return accounts.authenticate(request.email(), request.password());
    }

    record RegisterRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 10, max = 72) String password) {
    }

    record TokenRequest(@NotBlank String email, @NotBlank String password) {
    }
}

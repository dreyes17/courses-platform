package io.github.dreyes17.courses.identity.web;

import io.github.dreyes17.courses.enrollment.application.StudentView;
import io.github.dreyes17.courses.identity.application.AccessToken;
import io.github.dreyes17.courses.identity.application.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    @ApiResponse(responseCode = "409", description = "The email is already registered")
    @ApiResponse(responseCode = "429", description = "Too many sign-ups from this IP; retry after Retry-After seconds")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<StudentView> register(@Valid @RequestBody RegisterRequest request) {
        StudentView student = accounts.registerStudent(request.firstName(), request.lastName(), request.email(),
                request.password());
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/students/{id}").buildAndExpand(student.id());
        return ResponseEntity.created(location.toUri()).body(student);
    }

    @PostMapping("/token")
    @Operation(summary = "Exchange email and password for a bearer token (public)")
    @ApiResponse(responseCode = "200", description = "The bearer token and its lifetime")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @ApiResponse(responseCode = "429",
            description = "Too many login attempts from this IP; retry after Retry-After seconds")
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

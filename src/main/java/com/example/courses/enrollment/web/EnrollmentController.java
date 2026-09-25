package com.example.courses.enrollment.web;

import com.example.courses.enrollment.application.CourseEnrollmentView;
import com.example.courses.enrollment.application.EnrollmentService;
import com.example.courses.enrollment.application.EnrollmentView;
import com.example.courses.enrollment.application.StudentEnrollmentView;
import com.example.courses.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@Tag(name = "Enrollments")
class EnrollmentController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final EnrollmentService enrollments;

    EnrollmentController(EnrollmentService enrollments) {
        this.enrollments = enrollments;
    }

    @PostMapping("/api/enrollments")
    @Operation(summary = "Enroll a student in a published course",
            description = "Atomically reserves a seat and creates a PENDING payment; the enrollment is returned "
                    + "as PENDING_PAYMENT and becomes ACTIVE once the payment is confirmed asynchronously. "
                    + "Retrying with the same Idempotency-Key returns the original enrollment; reusing it for a "
                    + "different request returns 422. 409 when the course is full or the student is already enrolled.")
    ResponseEntity<EnrollmentView> enroll(
            @Parameter(description = "Client-generated unique key (e.g. a UUID) identifying this enrollment attempt")
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody EnrollRequest request) {
        EnrollmentView enrollment = enrollments.enroll(request.studentId(), request.courseId(), idempotencyKey);
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/enrollments/{id}").buildAndExpand(enrollment.id());
        return ResponseEntity.created(location.toUri()).body(enrollment);
    }

    @GetMapping("/api/enrollments/{id}")
    @Operation(summary = "Get an enrollment")
    EnrollmentView get(@PathVariable UUID id) {
        return enrollments.get(id);
    }

    @PutMapping("/api/enrollments/{id}/progress")
    @Operation(summary = "Update progress of an ACTIVE enrollment",
            description = "Progress cannot go backwards. Reaching 100 completes the enrollment and triggers "
                    + "certificate issuance asynchronously.")
    EnrollmentView updateProgress(@PathVariable UUID id, @Valid @RequestBody ProgressRequest request) {
        return enrollments.updateProgress(id, request.progress());
    }

    @PostMapping("/api/enrollments/{id}/cancel")
    @Operation(summary = "Cancel a PENDING_PAYMENT or ACTIVE enrollment and release its seat")
    EnrollmentView cancel(@PathVariable UUID id) {
        return enrollments.cancel(id);
    }

    @GetMapping("/api/courses/{courseId}/enrollments")
    @Operation(summary = "List the students enrolled in a course")
    PageResponse<CourseEnrollmentView> studentsOfCourse(
            @PathVariable UUID courseId,
            @ParameterObject @PageableDefault(size = 20, sort = "enrolledAt") Pageable pageable) {
        return PageResponse.from(enrollments.listStudentsOfCourse(courseId, pageable));
    }

    @GetMapping("/api/students/{studentId}/enrollments")
    @Operation(summary = "List the courses a student is enrolled in")
    PageResponse<StudentEnrollmentView> coursesOfStudent(
            @PathVariable UUID studentId,
            @ParameterObject @PageableDefault(size = 20, sort = "enrolledAt") Pageable pageable) {
        return PageResponse.from(enrollments.listCoursesOfStudent(studentId, pageable));
    }

    record EnrollRequest(@NotNull UUID studentId, @NotNull UUID courseId) {
    }

    record ProgressRequest(@NotNull @Min(0) @Max(100) Integer progress) {
    }
}

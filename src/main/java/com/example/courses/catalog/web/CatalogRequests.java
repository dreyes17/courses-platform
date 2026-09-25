package com.example.courses.catalog.web;

import com.example.courses.catalog.application.CourseTerms;
import com.example.courses.catalog.domain.CourseLevel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

final class CatalogRequests {

    private CatalogRequests() {
    }

    record CategoryRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String description) {
    }

    record CreateInstructorRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 5000) String bio,
            @NotBlank @Size(min = 10, max = 72) String password) {
    }

    record UpdateInstructorRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 5000) String bio) {
    }

    record CreateCourseRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull @Positive Integer durationHours,
            @NotNull CourseLevel level,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @Positive Integer capacity,
            @NotNull UUID categoryId,
            @NotNull UUID instructorId) {

        CourseTerms terms() {
            return new CourseTerms(title, description, durationHours, level, price, capacity);
        }
    }

    record UpdateCourseRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull @Positive Integer durationHours,
            @NotNull CourseLevel level,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @Positive Integer capacity) {

        CourseTerms terms() {
            return new CourseTerms(title, description, durationHours, level, price, capacity);
        }
    }
}

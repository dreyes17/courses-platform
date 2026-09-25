package com.example.courses.catalog.web;

import com.example.courses.catalog.application.CourseSearchCriteria;
import com.example.courses.catalog.application.CourseService;
import com.example.courses.catalog.application.CourseView;
import com.example.courses.catalog.web.CatalogRequests.CreateCourseRequest;
import com.example.courses.catalog.web.CatalogRequests.UpdateCourseRequest;
import com.example.courses.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Courses")
class CourseController {

    private final CourseService courses;

    CourseController(CourseService courses) {
        this.courses = courses;
    }

    @PostMapping
    @Operation(summary = "Create a course as DRAFT")
    ResponseEntity<CourseView> create(@Valid @RequestBody CreateCourseRequest request) {
        CourseView created = courses.createDraft(request.terms(), request.categoryId(), request.instructorId());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id());
        return ResponseEntity.created(location.toUri()).body(created);
    }

    @GetMapping
    @Operation(summary = "Search courses",
            description = "All filters are optional and combinable: categoryId, level, minPrice, maxPrice, "
                    + "title (case-insensitive substring), withAvailableSeats, status.")
    PageResponse<CourseView> search(@ParameterObject CourseSearchCriteria criteria,
                                    @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PageResponse.from(courses.search(criteria, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a course")
    CourseView get(@PathVariable UUID id) {
        return courses.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a course's details (capacity cannot drop below seats already taken)")
    CourseView update(@PathVariable UUID id, @Valid @RequestBody UpdateCourseRequest request) {
        return courses.update(id, request.terms());
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish a DRAFT course so students can enroll (409 from any other status)")
    CourseView publish(@PathVariable UUID id) {
        return courses.publish(id);
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a course; it stops accepting enrollments")
    CourseView archive(@PathVariable UUID id) {
        return courses.archive(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a DRAFT course (published courses must be archived instead)")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        courses.delete(id);
        return ResponseEntity.noContent().build();
    }
}

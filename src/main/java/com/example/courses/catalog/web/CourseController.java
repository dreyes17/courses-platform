package com.example.courses.catalog.web;

import com.example.courses.catalog.application.CourseSearchCriteria;
import com.example.courses.catalog.application.CourseService;
import com.example.courses.catalog.application.CourseView;
import com.example.courses.catalog.web.CatalogRequests.CreateCourseRequest;
import com.example.courses.catalog.web.CatalogRequests.UpdateCourseRequest;
import com.example.courses.shared.application.CursorPage;
import com.example.courses.shared.security.CurrentUser;
import com.example.courses.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Courses")
class CourseController {

    private static final String ADMIN_OR_COURSE_INSTRUCTOR =
            "hasRole('ADMIN') or @access.teachesCourse(authentication, #id)";

    private final CourseService courses;

    CourseController(CourseService courses) {
        this.courses = courses;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @access.isInstructor(authentication, #request.instructorId())")
    @Operation(summary = "Create a course as DRAFT. ADMIN, or an INSTRUCTOR for their own courses")
    @ApiResponse(responseCode = "404", description = "The category or the instructor doesn't exist")
    @ApiResponse(responseCode = "422", description = "The category is archived")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<CourseView> create(@Valid @RequestBody CreateCourseRequest request) {
        CourseView created = courses.createDraft(request.terms(), request.categoryId(), request.instructorId());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id());
        return ResponseEntity.created(location.toUri()).body(created);
    }

    @GetMapping
    @Operation(summary = "Search courses",
            description = "All filters are optional and combinable: categoryId, level, minPrice, maxPrice, "
                    + "title (case-insensitive substring), withAvailableSeats, status. "
                    + "Students only ever see PUBLISHED courses.")
    PageResponse<CourseView> search(@ParameterObject CourseSearchCriteria criteria,
                                    @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
                                    @AuthenticationPrincipal Jwt jwt) {
        return PageResponse.from(courses.search(criteria, pageable, onlyPublishedFor(jwt)));
    }

    @GetMapping("/scroll")
    @Operation(summary = "Search courses with cursor (keyset) pagination, newest first",
            description = "Same filters as GET /api/courses. Omit cursor for the first page, then send the "
                    + "nextCursor of each response as cursor; nextCursor is null on the last page. Unlike page "
                    + "numbers, courses created or removed while scrolling never cause duplicates or gaps. "
                    + "Students only ever see PUBLISHED courses.")
    CursorPage<CourseView> scroll(@ParameterObject CourseSearchCriteria criteria,
                                  @RequestParam(required = false) String cursor,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                  @AuthenticationPrincipal Jwt jwt) {
        return courses.scroll(criteria, cursor, size, onlyPublishedFor(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a course (404 for students when it isn't PUBLISHED)")
    CourseView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return courses.get(id, onlyPublishedFor(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN_OR_COURSE_INSTRUCTOR)
    @Operation(summary = "Update a course's details (capacity cannot drop below seats already taken). "
            + "ADMIN or the course's instructor")
    @ApiResponse(responseCode = "200", description = "The updated course")
    @ApiResponse(responseCode = "409", description = "The course was modified concurrently; reload it and retry")
    @ApiResponse(responseCode = "422", description = "The capacity is lower than the seats already taken")
    CourseView update(@PathVariable UUID id, @Valid @RequestBody UpdateCourseRequest request) {
        return courses.update(id, request.terms());
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize(ADMIN_OR_COURSE_INSTRUCTOR)
    @Operation(summary = "Publish a DRAFT course so students can enroll (409 from any other status). "
            + "ADMIN or the course's instructor")
    @ApiResponse(responseCode = "200", description = "The course, now PUBLISHED")
    @ApiResponse(responseCode = "409", description = "The course isn't DRAFT")
    CourseView publish(@PathVariable UUID id) {
        return courses.publish(id);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(ADMIN_OR_COURSE_INSTRUCTOR)
    @Operation(summary = "Archive a course; it stops accepting enrollments. ADMIN or the course's instructor")
    @ApiResponse(responseCode = "200", description = "The course, now ARCHIVED")
    @ApiResponse(responseCode = "409", description = "The course is already archived")
    CourseView archive(@PathVariable UUID id) {
        return courses.archive(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMIN_OR_COURSE_INSTRUCTOR)
    @Operation(summary = "Delete a DRAFT course (published courses must be archived instead). "
            + "ADMIN or the course's instructor")
    @ApiResponse(responseCode = "409", description = "The course isn't DRAFT")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        courses.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static boolean onlyPublishedFor(Jwt jwt) {
        return CurrentUser.from(jwt).isStudent();
    }
}

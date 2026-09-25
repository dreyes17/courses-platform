package com.example.courses.catalog.web;

import com.example.courses.catalog.application.InstructorService;
import com.example.courses.catalog.application.InstructorView;
import com.example.courses.catalog.web.CatalogRequests.CreateInstructorRequest;
import com.example.courses.catalog.web.CatalogRequests.UpdateInstructorRequest;
import com.example.courses.identity.application.AccountService;
import com.example.courses.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/instructors")
@Tag(name = "Instructors")
class InstructorController {

    private final InstructorService instructors;
    private final AccountService accounts;

    InstructorController(InstructorService instructors, AccountService accounts) {
        this.instructors = instructors;
        this.accounts = accounts;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create an instructor and their login account (email must be unique). ADMIN only")
    ResponseEntity<InstructorView> create(@Valid @RequestBody CreateInstructorRequest request) {
        InstructorView created = accounts.registerInstructor(request.name(), request.email(), request.bio(),
                request.password());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id());
        return ResponseEntity.created(location.toUri()).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List instructors. ADMIN only")
    PageResponse<InstructorView> list(@ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return PageResponse.from(instructors.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @access.isInstructor(authentication, #id)")
    @Operation(summary = "Get an instructor. ADMIN or the instructor themselves")
    InstructorView get(@PathVariable UUID id) {
        return instructors.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @access.isInstructor(authentication, #id)")
    @Operation(summary = "Update an instructor's name and bio (the email is immutable). ADMIN or the instructor themselves")
    InstructorView update(@PathVariable UUID id, @Valid @RequestBody UpdateInstructorRequest request) {
        return instructors.updateProfile(id, request.name(), request.bio());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an instructor with no courses (409 otherwise). ADMIN only")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        instructors.delete(id);
        return ResponseEntity.noContent().build();
    }
}

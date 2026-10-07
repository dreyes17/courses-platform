package io.github.dreyes17.courses.catalog.web;

import io.github.dreyes17.courses.catalog.application.InstructorService;
import io.github.dreyes17.courses.catalog.application.InstructorView;
import io.github.dreyes17.courses.catalog.web.CatalogRequests.CreateInstructorRequest;
import io.github.dreyes17.courses.catalog.web.CatalogRequests.UpdateInstructorRequest;
import io.github.dreyes17.courses.identity.application.AccountService;
import io.github.dreyes17.courses.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @ApiResponse(responseCode = "409", description = "The email is already used by another account")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<InstructorView> create(@Valid @RequestBody CreateInstructorRequest request) {
        InstructorView created = accounts.registerInstructor(request.name(), request.email(), request.bio(),
                request.password());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id());
        return ResponseEntity.created(location.toUri()).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List instructors. ADMIN only",
            description = "Optional filters, combinable: name and email (case-insensitive substrings).")
    PageResponse<InstructorView> list(@RequestParam(required = false) String name,
                                      @RequestParam(required = false) String email,
                                      @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return PageResponse.from(instructors.list(name, email, pageable));
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
    @Operation(summary = "Delete an instructor with no courses (409 otherwise), and their login account. ADMIN only")
    @ApiResponse(responseCode = "409", description = "The instructor still has courses")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        accounts.deleteInstructor(id);
        return ResponseEntity.noContent().build();
    }
}

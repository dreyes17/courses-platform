package com.example.courses.catalog.web;

import com.example.courses.catalog.application.CategoryService;
import com.example.courses.catalog.application.CategoryView;
import com.example.courses.catalog.web.CatalogRequests.CategoryRequest;
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
@RequestMapping("/api/categories")
@Tag(name = "Categories")
class CategoryController {

    private final CategoryService categories;

    CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a category (created ACTIVE). ADMIN only")
    ResponseEntity<CategoryView> create(@Valid @RequestBody CategoryRequest request) {
        CategoryView created = categories.create(request.name(), request.description());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id());
        return ResponseEntity.created(location.toUri()).body(created);
    }

    @GetMapping
    @Operation(summary = "List categories")
    PageResponse<CategoryView> list(@ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return PageResponse.from(categories.list(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a category")
    CategoryView get(@PathVariable UUID id) {
        return categories.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rename a category or change its description. ADMIN only")
    CategoryView update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return categories.update(id, request.name(), request.description());
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archive a category; no new courses can be created in it. ADMIN only")
    CategoryView archive(@PathVariable UUID id) {
        return categories.archive(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reactivate an archived category. ADMIN only")
    CategoryView activate(@PathVariable UUID id) {
        return categories.activate(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a category that has no courses (409 otherwise). ADMIN only")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        categories.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package io.github.dreyes17.courses.catalog.mcp;

import io.github.dreyes17.courses.catalog.application.CategoryService;
import io.github.dreyes17.courses.catalog.application.CategoryView;
import io.github.dreyes17.courses.catalog.application.CourseSearchCriteria;
import io.github.dreyes17.courses.catalog.application.CourseService;
import io.github.dreyes17.courses.catalog.application.CourseTerms;
import io.github.dreyes17.courses.catalog.application.CourseView;
import io.github.dreyes17.courses.catalog.application.CourseVisibility;
import io.github.dreyes17.courses.catalog.domain.CategoryStatus;
import io.github.dreyes17.courses.catalog.domain.CourseLevel;
import io.github.dreyes17.courses.catalog.domain.CourseStatus;
import io.github.dreyes17.courses.shared.mcp.Deleted;
import io.github.dreyes17.courses.shared.mcp.McpPaging;
import io.github.dreyes17.courses.shared.security.CurrentUser;
import io.github.dreyes17.courses.shared.web.PageResponse;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * MCP adapter for the catalog, like the REST controllers in {@code catalog.web}: each tool validates its input,
 * applies the same authorization rule as the equivalent endpoint and delegates to the same use case. An AI client
 * can therefore do exactly what its bearer token allows through the API, and nothing more.
 */
@Component
@Validated
class CatalogTools {

    private final CategoryService categories;
    private final CourseService courses;

    CatalogTools(CategoryService categories, CourseService courses) {
        this.categories = categories;
        this.courses = courses;
    }

    @McpTool(name = "list_categories", description = """
            List the course categories, alphabetically, one page at a time. The filters are optional and \
            combine with AND.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    public PageResponse<CategoryView> listCategories(
            @McpToolParam(description = "Case-insensitive text contained in the name", required = false)
            String name,
            @McpToolParam(description = "Only this status: ACTIVE or ARCHIVED", required = false)
            CategoryStatus status,
            @McpToolParam(description = McpPaging.PAGE_DESCRIPTION, required = false) Integer page,
            @McpToolParam(description = McpPaging.SIZE_DESCRIPTION, required = false) Integer size) {
        return PageResponse.from(categories.list(name, status, McpPaging.page(page, size, Sort.by("name"))));
    }

    @McpTool(name = "get_category", description = "Get one course category by id.",
            annotations = @McpAnnotations(readOnlyHint = true))
    public CategoryView getCategory(@McpToolParam(description = "Category id (UUID)") @NotNull UUID categoryId) {
        return categories.get(categoryId);
    }

    @McpTool(name = "create_category", description = "Create a course category. Its name must be unique. ADMIN only.",
            annotations = @McpAnnotations(destructiveHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryView createCategory(
            @McpToolParam(description = "Unique category name") @NotBlank @Size(max = 150) String name,
            @McpToolParam(description = "Optional description", required = false) @Size(max = 2000)
            String description) {
        return categories.create(name, description);
    }

    @McpTool(name = "update_category", description = """
            Rename a category or change its description. The name must stay unique. ADMIN only.""",
            annotations = @McpAnnotations(destructiveHint = false, idempotentHint = true))
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryView updateCategory(
            @McpToolParam(description = "Category id (UUID)") @NotNull UUID categoryId,
            @McpToolParam(description = "New unique category name") @NotBlank @Size(max = 150) String name,
            @McpToolParam(description = "New description; omit it to clear it", required = false) @Size(max = 2000)
            String description) {
        return categories.update(categoryId, name, description);
    }

    @McpTool(name = "archive_category", description = """
            Archive an ACTIVE category: no new courses can be created in it; its existing courses are kept. \
            ADMIN only.""")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryView archiveCategory(@McpToolParam(description = "Category id (UUID)") @NotNull UUID categoryId) {
        return categories.archive(categoryId);
    }

    @McpTool(name = "activate_category", description = """
            Reactivate an ARCHIVED category so courses can be created in it again. ADMIN only.""",
            annotations = @McpAnnotations(destructiveHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryView activateCategory(@McpToolParam(description = "Category id (UUID)") @NotNull UUID categoryId) {
        return categories.activate(categoryId);
    }

    @McpTool(name = "delete_category", description = """
            Delete a category that has no courses; one with courses can only be archived. ADMIN only.""")
    @PreAuthorize("hasRole('ADMIN')")
    public Deleted deleteCategory(@McpToolParam(description = "Category id (UUID)") @NotNull UUID categoryId) {
        categories.delete(categoryId);
        return new Deleted(categoryId);
    }

    @McpTool(name = "list_courses", description = """
            List courses, newest first, one page at a time. ADMIN sees every course, an INSTRUCTOR the \
            PUBLISHED ones plus their own in any status, and a STUDENT only PUBLISHED ones; use search_courses \
            to filter.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    public PageResponse<CourseView> listCourses(
            @McpToolParam(description = McpPaging.PAGE_DESCRIPTION, required = false) Integer page,
            @McpToolParam(description = McpPaging.SIZE_DESCRIPTION, required = false) Integer size) {
        var noFilters = new CourseSearchCriteria(null, null, null, null, null, null, null);
        return PageResponse.from(courses.search(noFilters, newestFirst(page, size), visibility()));
    }

    @McpTool(name = "search_courses", description = """
            Search courses, newest first. Every filter is optional and they combine with AND. Only returns \
            courses the caller can see: ADMIN every course, an INSTRUCTOR the PUBLISHED ones plus their own in \
            any status, a STUDENT only PUBLISHED ones.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    public PageResponse<CourseView> searchCourses(
            @McpToolParam(description = "Only courses in this category (UUID)", required = false) UUID categoryId,
            @McpToolParam(description = "Only this level: BEGINNER, INTERMEDIATE or ADVANCED", required = false)
            CourseLevel level,
            @McpToolParam(description = "Minimum price, inclusive", required = false) BigDecimal minPrice,
            @McpToolParam(description = "Maximum price, inclusive", required = false) BigDecimal maxPrice,
            @McpToolParam(description = "Case-insensitive text contained in the title", required = false)
            String title,
            @McpToolParam(description = "true: only courses with at least one free seat", required = false)
            Boolean withAvailableSeats,
            @McpToolParam(description = "Only this status: DRAFT, PUBLISHED or ARCHIVED",
                    required = false) CourseStatus status,
            @McpToolParam(description = McpPaging.PAGE_DESCRIPTION, required = false) Integer page,
            @McpToolParam(description = McpPaging.SIZE_DESCRIPTION, required = false) Integer size) {
        var criteria = new CourseSearchCriteria(categoryId, level, minPrice, maxPrice, title, withAvailableSeats,
                status);
        return PageResponse.from(courses.search(criteria, newestFirst(page, size), visibility()));
    }

    @McpTool(name = "get_course", description = """
            Get one course by id, including its free seats. A course that isn't PUBLISHED is 'not found' for \
            everyone but ADMIN and its own instructor.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    public CourseView getCourse(@McpToolParam(description = "Course id (UUID)") @NotNull UUID courseId) {
        return courses.get(courseId, visibility());
    }

    @McpTool(name = "create_course", description = """
            Create a course as DRAFT; students can't see it or enroll until publish_course. ADMIN, or an \
            INSTRUCTOR creating a course they teach.""",
            annotations = @McpAnnotations(destructiveHint = false))
    @PreAuthorize("hasRole('ADMIN') or @access.isInstructor(authentication, #instructorId)")
    public CourseView createCourse(
            @McpToolParam(description = "Course title") @NotBlank @Size(max = 200) String title,
            @McpToolParam(description = "Optional description", required = false) @Size(max = 5000)
            String description,
            @McpToolParam(description = "Estimated duration in hours, at least 1") @NotNull @Positive
            Integer durationHours,
            @McpToolParam(description = "BEGINNER, INTERMEDIATE or ADVANCED") @NotNull CourseLevel level,
            @McpToolParam(description = "Price, 0 or more, at most 2 decimals") @NotNull @DecimalMin("0.00")
            @Digits(integer = 10, fraction = 2) BigDecimal price,
            @McpToolParam(description = "Maximum number of seats, at least 1") @NotNull @Positive Integer capacity,
            @McpToolParam(description = "Category id (UUID)") @NotNull UUID categoryId,
            @McpToolParam(description = "Instructor id (UUID) of the course's instructor") @NotNull UUID instructorId) {
        var terms = new CourseTerms(title, description, durationHours, level, price, capacity);
        return courses.createDraft(terms, categoryId, instructorId);
    }

    @McpTool(name = "update_course", description = """
            Replace a course's details: title, description, duration, level, price and capacity. The capacity \
            can't drop below the seats already taken. ADMIN or the course's instructor.""",
            annotations = @McpAnnotations(destructiveHint = false, idempotentHint = true))
    @PreAuthorize("hasRole('ADMIN') or @access.teachesCourse(authentication, #courseId)")
    public CourseView updateCourse(
            @McpToolParam(description = "Course id (UUID)") @NotNull UUID courseId,
            @McpToolParam(description = "Course title") @NotBlank @Size(max = 200) String title,
            @McpToolParam(description = "Description; omit it to clear it", required = false) @Size(max = 5000)
            String description,
            @McpToolParam(description = "Estimated duration in hours, at least 1") @NotNull @Positive
            Integer durationHours,
            @McpToolParam(description = "BEGINNER, INTERMEDIATE or ADVANCED") @NotNull CourseLevel level,
            @McpToolParam(description = "Price, 0 or more, at most 2 decimals") @NotNull @DecimalMin("0.00")
            @Digits(integer = 10, fraction = 2) BigDecimal price,
            @McpToolParam(description = "Maximum number of seats, at least 1 and not below the seats taken")
            @NotNull @Positive Integer capacity) {
        var terms = new CourseTerms(title, description, durationHours, level, price, capacity);
        return courses.update(courseId, terms);
    }

    @McpTool(name = "delete_course", description = """
            Delete a DRAFT course. A course that was ever published can only be archived. ADMIN or the course's \
            instructor.""")
    @PreAuthorize("hasRole('ADMIN') or @access.teachesCourse(authentication, #courseId)")
    public Deleted deleteCourse(@McpToolParam(description = "Course id (UUID)") @NotNull UUID courseId) {
        courses.delete(courseId);
        return new Deleted(courseId);
    }

    @McpTool(name = "publish_course", description = """
            Publish a DRAFT course so students can find it and enroll. Fails for any other status. ADMIN or the \
            course's instructor.""",
            annotations = @McpAnnotations(destructiveHint = false))
    @PreAuthorize("hasRole('ADMIN') or @access.teachesCourse(authentication, #courseId)")
    public CourseView publishCourse(@McpToolParam(description = "Course id (UUID)") @NotNull UUID courseId) {
        return courses.publish(courseId);
    }

    @McpTool(name = "archive_course", description = """
            Archive a course: it stops accepting enrollments; existing ones are kept. ADMIN or the course's \
            instructor.""")
    @PreAuthorize("hasRole('ADMIN') or @access.teachesCourse(authentication, #courseId)")
    public CourseView archiveCourse(@McpToolParam(description = "Course id (UUID)") @NotNull UUID courseId) {
        return courses.archive(courseId);
    }

    private static Pageable newestFirst(Integer page, Integer size) {
        return McpPaging.page(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static CourseVisibility visibility() {
        CurrentUser user = CurrentUser.from(SecurityContextHolder.getContext().getAuthentication());
        return user == null
                ? CourseVisibility.PUBLISHED_ONLY
                : CourseVisibility.of(user.isAdmin(), user.instructorId());
    }
}

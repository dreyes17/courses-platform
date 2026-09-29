package com.example.courses.catalog.mcp;

import com.example.courses.catalog.application.CategoryService;
import com.example.courses.catalog.application.CategoryView;
import com.example.courses.catalog.application.CourseSearchCriteria;
import com.example.courses.catalog.application.CourseService;
import com.example.courses.catalog.application.CourseTerms;
import com.example.courses.catalog.application.CourseView;
import com.example.courses.catalog.domain.CategoryStatus;
import com.example.courses.catalog.domain.CourseLevel;
import com.example.courses.catalog.domain.CourseStatus;
import com.example.courses.shared.mcp.McpPaging;
import com.example.courses.shared.security.CurrentUser;
import com.example.courses.shared.web.PageResponse;
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

    @McpTool(name = "list_courses", description = """
            List courses, newest first, one page at a time. Students only see PUBLISHED courses; \
            use search_courses to filter.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    public PageResponse<CourseView> listCourses(
            @McpToolParam(description = McpPaging.PAGE_DESCRIPTION, required = false) Integer page,
            @McpToolParam(description = McpPaging.SIZE_DESCRIPTION, required = false) Integer size) {
        var noFilters = new CourseSearchCriteria(null, null, null, null, null, null, null);
        return PageResponse.from(courses.search(noFilters, newestFirst(page, size), onlyPublished()));
    }

    @McpTool(name = "search_courses", description = """
            Search courses, newest first. Every filter is optional and they combine with AND. Students only \
            see PUBLISHED courses, whatever the status filter says.""",
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
            @McpToolParam(description = "Only this status: DRAFT, PUBLISHED or ARCHIVED (ignored for students)",
                    required = false) CourseStatus status,
            @McpToolParam(description = McpPaging.PAGE_DESCRIPTION, required = false) Integer page,
            @McpToolParam(description = McpPaging.SIZE_DESCRIPTION, required = false) Integer size) {
        var criteria = new CourseSearchCriteria(categoryId, level, minPrice, maxPrice, title, withAvailableSeats,
                status);
        return PageResponse.from(courses.search(criteria, newestFirst(page, size), onlyPublished()));
    }

    @McpTool(name = "get_course", description = """
            Get one course by id, including its free seats. Students get 'not found' for courses that aren't \
            PUBLISHED.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    public CourseView getCourse(@McpToolParam(description = "Course id (UUID)") @NotNull UUID courseId) {
        return courses.get(courseId, onlyPublished());
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

    private static boolean onlyPublished() {
        CurrentUser user = CurrentUser.from(SecurityContextHolder.getContext().getAuthentication());
        return user == null || user.isStudent();
    }
}

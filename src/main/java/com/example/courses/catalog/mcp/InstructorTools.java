package com.example.courses.catalog.mcp;

import com.example.courses.catalog.application.InstructorService;
import com.example.courses.catalog.application.InstructorView;
import com.example.courses.identity.application.AccountService;
import com.example.courses.shared.mcp.Deleted;
import com.example.courses.shared.mcp.McpPaging;
import com.example.courses.shared.web.PageResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

/** MCP adapter for instructors, with the same rules as {@code InstructorController}. */
@Component
@Validated
class InstructorTools {

    private final InstructorService instructors;
    private final AccountService accounts;

    InstructorTools(InstructorService instructors, AccountService accounts) {
        this.instructors = instructors;
        this.accounts = accounts;
    }

    @McpTool(name = "create_instructor", description = """
            Create an instructor and the login account they use to manage their courses. The email must not be \
            used by any other account. ADMIN only.""",
            annotations = @McpAnnotations(destructiveHint = false))
    @PreAuthorize("hasRole('ADMIN')")
    public InstructorView createInstructor(
            @McpToolParam(description = "Full name") @NotBlank @Size(max = 150) String name,
            @McpToolParam(description = "Unique email; it is also the login") @NotBlank @Email @Size(max = 255)
            String email,
            @McpToolParam(description = "Optional biography", required = false) @Size(max = 5000) String bio,
            @McpToolParam(description = "Initial password, 10 to 72 characters") @NotBlank @Size(min = 10, max = 72)
            String password) {
        return accounts.registerInstructor(name, email, bio, password);
    }

    @McpTool(name = "list_instructors", description = """
            List instructors alphabetically, one page at a time. The filters are optional and combine with AND. \
            ADMIN only.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<InstructorView> listInstructors(
            @McpToolParam(description = "Case-insensitive text contained in the name", required = false)
            String name,
            @McpToolParam(description = "Case-insensitive text contained in the email", required = false)
            String email,
            @McpToolParam(description = McpPaging.PAGE_DESCRIPTION, required = false) Integer page,
            @McpToolParam(description = McpPaging.SIZE_DESCRIPTION, required = false) Integer size) {
        return PageResponse.from(instructors.list(name, email, McpPaging.page(page, size, Sort.by("name"))));
    }

    @McpTool(name = "get_instructor", description = "Get one instructor by id. ADMIN or the instructor themselves.",
            annotations = @McpAnnotations(readOnlyHint = true))
    @PreAuthorize("hasRole('ADMIN') or @access.isInstructor(authentication, #instructorId)")
    public InstructorView getInstructor(
            @McpToolParam(description = "Instructor id (UUID)") @NotNull UUID instructorId) {
        return instructors.get(instructorId);
    }

    @McpTool(name = "update_instructor", description = """
            Update an instructor's name and biography; the email can't change. ADMIN or the instructor \
            themselves.""",
            annotations = @McpAnnotations(destructiveHint = false, idempotentHint = true))
    @PreAuthorize("hasRole('ADMIN') or @access.isInstructor(authentication, #instructorId)")
    public InstructorView updateInstructor(
            @McpToolParam(description = "Instructor id (UUID)") @NotNull UUID instructorId,
            @McpToolParam(description = "Full name") @NotBlank @Size(max = 150) String name,
            @McpToolParam(description = "Biography; omit it to clear it", required = false) @Size(max = 5000)
            String bio) {
        return instructors.updateProfile(instructorId, name, bio);
    }

    @McpTool(name = "delete_instructor", description = """
            Delete an instructor who teaches no courses, and their login account. ADMIN only.""")
    @PreAuthorize("hasRole('ADMIN')")
    public Deleted deleteInstructor(@McpToolParam(description = "Instructor id (UUID)") @NotNull UUID instructorId) {
        accounts.deleteInstructor(instructorId);
        return new Deleted(instructorId);
    }
}

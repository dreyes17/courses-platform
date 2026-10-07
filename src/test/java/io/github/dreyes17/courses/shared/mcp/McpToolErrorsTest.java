package io.github.dreyes17.courses.shared.mcp;

import io.github.dreyes17.courses.shared.application.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolErrorsTest {

    @Test
    void applicationMessagesReachTheClient() {
        UUID id = UUID.randomUUID();

        assertThat(McpToolErrors.clientMessage(new ResourceNotFoundException("Course", id)))
                .isEqualTo("Course " + id + " not found");
    }

    @Test
    void internalDetailsNeverReachTheClient() {
        assertThat(McpToolErrors.clientMessage(new IllegalStateException("column users.password_hash is null")))
                .isEqualTo("An unexpected error occurred");
        assertThat(McpToolErrors.clientMessage(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_categories_name\"")))
                .doesNotContain("uq_categories_name");
    }
}

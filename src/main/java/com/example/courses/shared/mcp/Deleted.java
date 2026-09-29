package com.example.courses.shared.mcp;

import java.util.UUID;

/** What a delete tool returns, where the REST API answers 204: the id of the resource it removed. */
public record Deleted(UUID deletedId) {
}

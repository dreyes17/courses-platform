package com.example.courses.shared.mcp;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Page and size as MCP tools receive them: optional, and bounded like the REST API's pageable parameters. */
public final class McpPaging {

    public static final String PAGE_DESCRIPTION = "Zero-based page number (default 0)";
    public static final String SIZE_DESCRIPTION = "Page size, 1 to 100 (default 20)";

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private McpPaging() {
    }

    public static Pageable page(Integer page, Integer size, Sort sort) {
        int number = page == null ? 0 : Math.max(page, 0);
        int bounded = size == null ? DEFAULT_SIZE : Math.clamp(size, 1, MAX_SIZE);
        return PageRequest.of(number, bounded, sort);
    }
}

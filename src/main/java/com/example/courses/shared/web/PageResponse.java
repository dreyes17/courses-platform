package com.example.courses.shared.web;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable pagination contract for the API, instead of serializing Spring Data's PageImpl. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}

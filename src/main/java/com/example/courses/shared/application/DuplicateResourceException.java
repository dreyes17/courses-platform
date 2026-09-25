package com.example.courses.shared.application;

import com.example.courses.shared.domain.ConflictException;

public class DuplicateResourceException extends ConflictException {

    public DuplicateResourceException(String resource, String field, String value) {
        super("%s with %s '%s' already exists".formatted(resource, field, value));
    }
}

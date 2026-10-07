package io.github.dreyes17.courses.shared.application;

import io.github.dreyes17.courses.shared.domain.ConflictException;

public class DuplicateResourceException extends ConflictException {

    public DuplicateResourceException(String resource, String field, String value) {
        super("%s with %s '%s' already exists".formatted(resource, field, value));
    }
}

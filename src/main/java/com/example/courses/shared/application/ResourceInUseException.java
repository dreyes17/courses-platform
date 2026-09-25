package com.example.courses.shared.application;

import com.example.courses.shared.domain.ConflictException;

public class ResourceInUseException extends ConflictException {

    public ResourceInUseException(String resource, Object id, String reason) {
        super("%s %s cannot be deleted: %s".formatted(resource, id, reason));
    }
}

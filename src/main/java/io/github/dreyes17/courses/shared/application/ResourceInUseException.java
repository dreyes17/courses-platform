package io.github.dreyes17.courses.shared.application;

import io.github.dreyes17.courses.shared.domain.ConflictException;

public class ResourceInUseException extends ConflictException {

    public ResourceInUseException(String resource, Object id, String reason) {
        super("%s %s cannot be deleted: %s".formatted(resource, id, reason));
    }
}

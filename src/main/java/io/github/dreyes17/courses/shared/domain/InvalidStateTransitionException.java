package io.github.dreyes17.courses.shared.domain;

import java.util.UUID;

public class InvalidStateTransitionException extends ConflictException {

    public InvalidStateTransitionException(String aggregate, UUID id, Enum<?> currentStatus, String attemptedAction) {
        super("%s %s cannot %s while in status %s".formatted(aggregate, id, attemptedAction, currentStatus));
    }
}

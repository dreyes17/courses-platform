package com.example.courses.shared.domain;

/** The request is valid but clashes with the current state of the system (HTTP 409). */
public abstract class ConflictException extends RuntimeException {

    protected ConflictException(String message) {
        super(message);
    }
}

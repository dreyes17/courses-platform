package com.example.courses.idempotency.application;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key %s was already used with a different request".formatted(key));
    }
}

package com.example.courses.messaging.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}

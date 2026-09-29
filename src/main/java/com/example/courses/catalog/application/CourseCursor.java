package com.example.courses.catalog.application;

import com.example.courses.catalog.domain.Course;
import com.example.courses.shared.application.InvalidCursorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Position in the "newest first" course order: the (createdAt, id) of the last course returned. The id breaks
 * ties between courses created in the same instant, so the order is total and no course is skipped or
 * repeated. Encoded as URL-safe Base64 so clients treat it as opaque.
 */
record CourseCursor(Instant createdAt, UUID id) {

    static CourseCursor after(Course course) {
        return new CourseCursor(course.getCreatedAt(), course.getId());
    }

    String encode() {
        String raw = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static CourseCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            return new CourseCursor(Instant.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException e) {
            throw new InvalidCursorException();
        }
    }
}

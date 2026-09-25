package com.example.courses.catalog.repository;

import com.example.courses.catalog.domain.Course;
import com.example.courses.catalog.domain.CourseLevel;
import com.example.courses.catalog.domain.CourseStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CourseSpecifications {

    private CourseSpecifications() {
    }

    /** Null criteria are ignored, so any combination of filters can be sent. */
    public static Specification<Course> matching(UUID categoryId, CourseLevel level, BigDecimal minPrice,
                                                 BigDecimal maxPrice, String titleContains, Boolean withAvailableSeats,
                                                 CourseStatus status) {
        List<Specification<Course>> filters = new ArrayList<>();
        if (categoryId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (level != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("level"), level));
        }
        if (minPrice != null) {
            filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        }
        if (maxPrice != null) {
            filters.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice));
        }
        if (titleContains != null && !titleContains.isBlank()) {
            String pattern = "%" + escapeLike(titleContains.toLowerCase(Locale.ROOT)) + "%";
            filters.add((root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\'));
        }
        if (Boolean.TRUE.equals(withAvailableSeats)) {
            filters.add((root, query, cb) -> cb.lessThan(root.get("seatsTaken"), root.get("capacity")));
        }
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return Specification.allOf(filters);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

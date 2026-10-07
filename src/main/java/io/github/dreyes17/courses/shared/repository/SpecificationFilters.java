package io.github.dreyes17.courses.shared.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Building blocks for the optional filters of list endpoints. A filter whose value is null (or blank text) was not
 * requested: it returns null and {@link #allOf} skips it.
 */
public final class SpecificationFilters {

    private SpecificationFilters() {
    }

    @SafeVarargs
    public static <T> Specification<T> allOf(Specification<T>... filters) {
        return Specification.allOf(Arrays.stream(filters).filter(Objects::nonNull).toList());
    }

    public static <T> Specification<T> equalTo(String attribute, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    /** Case-insensitive substring of any of the attributes. LIKE wildcards in the text match literally. */
    public static <T> Specification<T> containsIgnoringCase(String text, String... attributes) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(Arrays.stream(attributes)
                .map(attribute -> cb.like(cb.lower(root.get(attribute)), pattern, '\\'))
                .toArray(Predicate[]::new));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

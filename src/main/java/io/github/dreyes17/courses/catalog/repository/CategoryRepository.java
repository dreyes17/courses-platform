package io.github.dreyes17.courses.catalog.repository;

import io.github.dreyes17.courses.catalog.domain.Category;
import io.github.dreyes17.courses.catalog.domain.CategoryStatus;
import io.github.dreyes17.courses.shared.repository.SpecificationFilters;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID>, JpaSpecificationExecutor<Category> {

    /** Null filters are ignored. */
    static Specification<Category> matching(String nameContains, CategoryStatus status) {
        return SpecificationFilters.allOf(
                SpecificationFilters.containsIgnoringCase(nameContains, "name"),
                SpecificationFilters.equalTo("status", status));
    }

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}

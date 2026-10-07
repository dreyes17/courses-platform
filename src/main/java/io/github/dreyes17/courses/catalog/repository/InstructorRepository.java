package io.github.dreyes17.courses.catalog.repository;

import io.github.dreyes17.courses.catalog.domain.Instructor;
import io.github.dreyes17.courses.shared.repository.SpecificationFilters;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface InstructorRepository extends JpaRepository<Instructor, UUID>, JpaSpecificationExecutor<Instructor> {

    /** Null filters are ignored. */
    static Specification<Instructor> matching(String nameContains, String emailContains) {
        return SpecificationFilters.allOf(
                SpecificationFilters.containsIgnoringCase(nameContains, "name"),
                SpecificationFilters.containsIgnoringCase(emailContains, "email"));
    }

    Optional<Instructor> findByEmail(String email);

    boolean existsByEmail(String email);
}

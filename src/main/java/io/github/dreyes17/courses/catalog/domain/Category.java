package io.github.dreyes17.courses.catalog.domain;

import io.github.dreyes17.courses.shared.domain.BaseEntity;
import io.github.dreyes17.courses.shared.domain.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Category() {
    }

    private Category(String name, String description) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = description;
        this.status = CategoryStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public static Category create(String name, String description) {
        return new Category(name, description);
    }

    public void rename(String name, String description) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = description;
    }

    public void archive() {
        if (status == CategoryStatus.ARCHIVED) {
            throw new InvalidStateTransitionException("Category", getId(), status, "be archived");
        }
        status = CategoryStatus.ARCHIVED;
    }

    public void activate() {
        if (status == CategoryStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Category", getId(), status, "be activated");
        }
        status = CategoryStatus.ACTIVE;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CategoryStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

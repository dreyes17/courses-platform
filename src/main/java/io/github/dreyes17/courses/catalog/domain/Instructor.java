package io.github.dreyes17.courses.catalog.domain;

import io.github.dreyes17.courses.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "instructors")
public class Instructor extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Instructor() {
    }

    private Instructor(String name, String email, String bio) {
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.bio = bio;
        this.createdAt = Instant.now();
    }

    public static Instructor create(String name, String email, String bio) {
        return new Instructor(name, email, bio);
    }

    public void updateProfile(String name, String bio) {
        this.name = Objects.requireNonNull(name, "name");
        this.bio = bio;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getBio() {
        return bio;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

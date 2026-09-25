package com.example.courses.identity.domain;

import com.example.courses.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Login identity. Holds only a password hash, never the password itself. */
@Entity
@Table(name = "users")
public class UserAccount extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "student_id", unique = true)
    private UUID studentId;

    @Column(name = "instructor_id", unique = true)
    private UUID instructorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    private UserAccount(String email, String passwordHash, Role role, UUID studentId, UUID instructorId) {
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.role = role;
        this.studentId = studentId;
        this.instructorId = instructorId;
        this.createdAt = Instant.now();
    }

    public static UserAccount admin(String email, String passwordHash) {
        return new UserAccount(email, passwordHash, Role.ADMIN, null, null);
    }

    public static UserAccount student(String email, String passwordHash, UUID studentId) {
        return new UserAccount(email, passwordHash, Role.STUDENT, Objects.requireNonNull(studentId), null);
    }

    public static UserAccount instructor(String email, String passwordHash, UUID instructorId) {
        return new UserAccount(email, passwordHash, Role.INSTRUCTOR, null, Objects.requireNonNull(instructorId));
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getInstructorId() {
        return instructorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.example.courses.catalog.domain;

import com.example.courses.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "courses")
public class Course extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "duration_hours", nullable = false)
    private int durationHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseLevel level;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "seats_taken", nullable = false)
    private int seatsTaken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private Instructor instructor;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Course() {
    }

    private Course(String title, String description, int durationHours, CourseLevel level, BigDecimal price,
                    int capacity, Category category, Instructor instructor) {
        if (durationHours <= 0) {
            throw new IllegalArgumentException("durationHours must be positive");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price cannot be negative");
        }
        this.title = Objects.requireNonNull(title, "title");
        this.description = description;
        this.durationHours = durationHours;
        this.level = Objects.requireNonNull(level, "level");
        this.price = price;
        this.capacity = capacity;
        this.seatsTaken = 0;
        this.status = CourseStatus.DRAFT;
        this.category = Objects.requireNonNull(category, "category");
        this.instructor = Objects.requireNonNull(instructor, "instructor");
        this.createdAt = Instant.now();
    }

    public static Course draft(String title, String description, int durationHours, CourseLevel level,
                                BigDecimal price, int capacity, Category category, Instructor instructor) {
        return new Course(title, description, durationHours, level, price, capacity, category, instructor);
    }

    public void updateDetails(String title, String description, int durationHours, CourseLevel level,
                               BigDecimal price, int capacity) {
        if (capacity < seatsTaken) {
            throw new IllegalArgumentException(
                    "capacity %d cannot be lower than seats already taken %d".formatted(capacity, seatsTaken));
        }
        this.title = Objects.requireNonNull(title, "title");
        this.description = description;
        this.durationHours = durationHours;
        this.level = Objects.requireNonNull(level, "level");
        this.price = price;
        this.capacity = capacity;
    }

    public void publish() {
        if (status != CourseStatus.DRAFT) {
            throw new InvalidCourseStateException(getId(), status, "be published");
        }
        status = CourseStatus.PUBLISHED;
    }

    public void archive() {
        if (status == CourseStatus.ARCHIVED) {
            throw new InvalidCourseStateException(getId(), status, "be archived");
        }
        status = CourseStatus.ARCHIVED;
    }

    /**
     * Seats are reserved with an atomic conditional UPDATE (see {@code CourseRepository#tryReserveSeat});
     * when that update matches no row, this explains why with the matching domain exception.
     */
    public void assertAcceptsEnrollment() {
        if (status != CourseStatus.PUBLISHED) {
            throw new InvalidCourseStateException(getId(), status, "accept enrollments");
        }
        if (seatsTaken >= capacity) {
            throw new CourseFullException(getId());
        }
    }

    public boolean hasAvailableSeats() {
        return seatsTaken < capacity;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationHours() {
        return durationHours;
    }

    public CourseLevel getLevel() {
        return level;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getSeatsTaken() {
        return seatsTaken;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public Category getCategory() {
        return category;
    }

    public Instructor getInstructor() {
        return instructor;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

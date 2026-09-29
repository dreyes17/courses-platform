package com.example.courses.catalog.application;

import com.example.courses.catalog.domain.Course;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.example.courses.support.DomainFixtures.publishedCourse;
import static com.example.courses.support.DomainFixtures.withSeatsTaken;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The compiler already rejects a view field with no source (unmappedTargetPolicy = ERROR); these tests cover
 * what it can't see: that nested and derived fields come from the right place.
 */
class CatalogViewMapperTest {

    private final CatalogViewMapper mapper = new CatalogViewMapperImpl();

    @Test
    void courseViewFlattensCategoryAndInstructorAndDerivesAvailableSeats() {
        Course course = withSeatsTaken(publishedCourse(10, new BigDecimal("49.90")), 3);

        CourseView view = mapper.toView(course);

        assertThat(view.id()).isEqualTo(course.getId());
        assertThat(view.price()).isEqualByComparingTo("49.90");
        assertThat(view.capacity()).isEqualTo(10);
        assertThat(view.seatsTaken()).isEqualTo(3);
        assertThat(view.availableSeats()).isEqualTo(7);
        assertThat(view.status()).isEqualTo(course.getStatus());
        assertThat(view.categoryId()).isEqualTo(course.getCategory().getId());
        assertThat(view.categoryName()).isEqualTo(course.getCategory().getName());
        assertThat(view.instructorId()).isEqualTo(course.getInstructor().getId());
        assertThat(view.instructorName()).isEqualTo(course.getInstructor().getName());
    }
}

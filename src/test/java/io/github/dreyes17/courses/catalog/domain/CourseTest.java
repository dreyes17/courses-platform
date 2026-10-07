package io.github.dreyes17.courses.catalog.domain;

import io.github.dreyes17.courses.shared.domain.BusinessRuleViolationException;
import io.github.dreyes17.courses.shared.domain.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.github.dreyes17.courses.support.DomainFixtures.category;
import static io.github.dreyes17.courses.support.DomainFixtures.draftCourse;
import static io.github.dreyes17.courses.support.DomainFixtures.instructor;
import static io.github.dreyes17.courses.support.DomainFixtures.publishedCourse;
import static io.github.dreyes17.courses.support.DomainFixtures.withSeatsTaken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseTest {

    @Test
    void newCourseStartsAsDraftWithNoSeatsTaken() {
        Course course = draftCourse(20, BigDecimal.TEN);

        assertThat(course.getStatus()).isEqualTo(CourseStatus.DRAFT);
        assertThat(course.getSeatsTaken()).isZero();
    }

    @Test
    void cannotBeCreatedInAnArchivedCategory() {
        Category archived = category();
        archived.archive();

        assertThatThrownBy(() -> Course.draft("Spring", null, 10, CourseLevel.BEGINNER, BigDecimal.TEN, 5,
                archived, instructor()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void rejectsNonPositiveCapacityOrDurationAndNegativePrice() {
        assertThatThrownBy(() -> draftCourse(0, BigDecimal.TEN)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> draftCourse(5, new BigDecimal("-1")))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> Course.draft("Spring", null, 0, CourseLevel.BEGINNER, BigDecimal.TEN, 5,
                category(), instructor()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void onlyADraftCanBePublished() {
        Course course = publishedCourse(5, BigDecimal.TEN);

        assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
        assertThatThrownBy(course::publish).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void archivingTwiceIsRejected() {
        Course course = publishedCourse(5, BigDecimal.TEN);
        course.archive();

        assertThatThrownBy(course::archive).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void acceptsEnrollmentsOnlyWhenPublishedWithFreeSeats() {
        assertThatThrownBy(() -> draftCourse(5, BigDecimal.TEN).assertAcceptsEnrollment())
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> withSeatsTaken(publishedCourse(5, BigDecimal.TEN), 5).assertAcceptsEnrollment())
                .isInstanceOf(CourseFullException.class);
        assertThatNoException().isThrownBy(() ->
                withSeatsTaken(publishedCourse(5, BigDecimal.TEN), 4).assertAcceptsEnrollment());
    }

    @Test
    void capacityCannotDropBelowSeatsAlreadyTaken() {
        Course course = withSeatsTaken(publishedCourse(10, BigDecimal.TEN), 6);

        assertThatThrownBy(() -> course.updateDetails("Spring", null, 10, CourseLevel.BEGINNER, BigDecimal.TEN, 5))
                .isInstanceOf(BusinessRuleViolationException.class);
        course.updateDetails("Spring", null, 10, CourseLevel.BEGINNER, BigDecimal.TEN, 6);
        assertThat(course.getCapacity()).isEqualTo(6);
    }

    @Test
    void onlyDraftsCanBeDeleted() {
        assertThatNoException().isThrownBy(() -> draftCourse(5, BigDecimal.TEN).assertDeletable());
        assertThatThrownBy(() -> publishedCourse(5, BigDecimal.TEN).assertDeletable())
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}

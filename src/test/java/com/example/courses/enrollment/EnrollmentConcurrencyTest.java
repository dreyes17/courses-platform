package com.example.courses.enrollment;

import com.example.courses.AbstractIntegrationTest;
import com.example.courses.catalog.domain.CourseFullException;
import com.example.courses.enrollment.application.EnrollmentService;
import com.example.courses.enrollment.repository.EnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private EnrollmentRepository enrollments;

    @Test
    void neverExceedsCapacityWhenManyStudentsCompeteForTheLastSeats() throws Exception {
        int capacity = 3;
        int contenders = 20;
        UUID courseId = publishedCourse(capacity, new BigDecimal("50.00"));
        List<UUID> studentIds = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            studentIds.add(student());
        }

        var start = new CountDownLatch(1);
        List<Future<Outcome>> outcomes = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(contenders)) {
            for (UUID studentId : studentIds) {
                outcomes.add(executor.submit(() -> {
                    start.await();
                    try {
                        enrollmentService.enroll(studentId, courseId, null);
                        return Outcome.ENROLLED;
                    } catch (CourseFullException e) {
                        return Outcome.COURSE_FULL;
                    }
                }));
            }
            start.countDown();
        }

        List<Outcome> results = new ArrayList<>();
        for (Future<Outcome> outcome : outcomes) {
            results.add(outcome.get());
        }
        assertThat(results).filteredOn(Outcome.ENROLLED::equals).hasSize(capacity);
        assertThat(results).filteredOn(Outcome.COURSE_FULL::equals).hasSize(contenders - capacity);
        assertThat(seatsTaken(courseId)).isEqualTo(capacity);
        assertThat(enrollments.findByCourseId(courseId, Pageable.unpaged()).getTotalElements()).isEqualTo(capacity);
    }

    private enum Outcome { ENROLLED, COURSE_FULL }
}

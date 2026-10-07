package io.github.dreyes17.courses;

import io.github.dreyes17.courses.catalog.domain.Category;
import io.github.dreyes17.courses.catalog.domain.Course;
import io.github.dreyes17.courses.catalog.domain.CourseLevel;
import io.github.dreyes17.courses.catalog.domain.Instructor;
import io.github.dreyes17.courses.catalog.repository.CategoryRepository;
import io.github.dreyes17.courses.catalog.repository.CourseRepository;
import io.github.dreyes17.courses.catalog.repository.InstructorRepository;
import io.github.dreyes17.courses.enrollment.domain.Student;
import io.github.dreyes17.courses.enrollment.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

/** Every integration test shares these properties, so they all reuse one context and one set of containers. */
@SpringBootTest(properties = {
        "app.outbox.poll-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=500ms",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "io.github.dreyes17.courses.support.SqlStatementCounter",
        "app.jwt.secret=test-only-secret-at-least-32-bytes-long!",
        // The suite logs in hundreds of times from one address; RateLimitingTest enables it on its own.
        "app.rate-limit.enabled=false",
        "management.tracing.sampling.probability=1.0",
        "app.security.admin.email=" + AbstractIntegrationTest.ADMIN_EMAIL,
        "app.security.admin.password=" + AbstractIntegrationTest.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
@AutoConfigureTracing
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final String ADMIN_EMAIL = "admin@courses.test";
    protected static final String ADMIN_PASSWORD = "admin-password-for-tests";

    @Autowired
    protected TransactionTemplate transactionTemplate;
    @Autowired
    protected CategoryRepository categories;
    @Autowired
    protected InstructorRepository instructors;
    @Autowired
    protected CourseRepository courses;
    @Autowired
    protected StudentRepository students;

    protected UUID publishedCourse(int capacity, BigDecimal price) {
        return transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Category category = categories.save(Category.create("Category " + suffix, null));
            Instructor instructor = instructors.save(Instructor.create("Instructor", suffix + "@teach.test", null));
            Course course = Course.draft("Course " + suffix, null, 10, CourseLevel.BEGINNER, price, capacity,
                    category, instructor);
            course.publish();
            return courses.save(course).getId();
        });
    }

    protected UUID student() {
        return transactionTemplate.execute(status -> students.save(
                Student.register("Ada", "Lovelace", UUID.randomUUID() + "@learn.test")).getId());
    }

    protected int seatsTaken(UUID courseId) {
        return courses.findById(courseId).orElseThrow().getSeatsTaken();
    }
}

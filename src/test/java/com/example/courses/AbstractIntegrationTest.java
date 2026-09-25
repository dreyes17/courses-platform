package com.example.courses;

import com.example.courses.catalog.domain.Category;
import com.example.courses.catalog.domain.Course;
import com.example.courses.catalog.domain.CourseLevel;
import com.example.courses.catalog.domain.Instructor;
import com.example.courses.catalog.repository.CategoryRepository;
import com.example.courses.catalog.repository.CourseRepository;
import com.example.courses.catalog.repository.InstructorRepository;
import com.example.courses.enrollment.domain.Student;
import com.example.courses.enrollment.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

/** Every integration test shares these properties, so they all reuse one context and one set of containers. */
@SpringBootTest(properties = {
        "app.outbox.poll-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=500ms"
})
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

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

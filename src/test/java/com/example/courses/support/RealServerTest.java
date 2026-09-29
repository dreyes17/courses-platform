package com.example.courses.support;

import com.example.courses.TestcontainersConfiguration;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For tests that need real servers on real ports (API and management), instead of MockMvc. Every class with this
 * annotation shares one application context. {@code @AutoConfigureMetrics} re-enables the Prometheus registry,
 * which Spring Boot disables in tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "app.jwt.secret=test-only-secret-at-least-32-bytes-long!",
        "app.security.admin.email=" + RealServerTest.ADMIN_EMAIL,
        "app.security.admin.password=" + RealServerTest.ADMIN_PASSWORD
})
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
public @interface RealServerTest {

    String ADMIN_EMAIL = "admin@courses.test";
    String ADMIN_PASSWORD = "admin-password-for-tests";
}

package com.example.courses.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityApiTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void requestWithoutTokenGets401Problem() {
        var result = get("/api/courses", null);

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).headers().containsHeader(HttpHeaders.WWW_AUTHENTICATE);
    }

    @Test
    void tokenSignedWithAnotherKeyGets401() {
        var foreignKey = new SecretKeySpec(
                "some-other-secret-that-is-32-bytes-long".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var claims = JwtClaimsSet.builder().issuer("courses-api").subject(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusSeconds(600)).claim("roles", List.of("ADMIN")).build();
        String forged = NimbusJwtEncoder.withSecretKey(foreignKey).algorithm(MacAlgorithm.HS256).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        assertThat(get("/api/students", forged)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongPasswordGets401WithoutRevealingWhichPartFailed() {
        Account student = registerStudent();

        var wrongPassword = post("/api/auth/token", null, """
                {"email": "%s", "password": "not-the-password"}""".formatted(student.email()));
        var unknownEmail = post("/api/auth/token", null, """
                {"email": "nobody-%s@learn.test", "password": "whatever-password"}""".formatted(unique()));

        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(body(wrongPassword).get("detail")).isEqualTo(body(unknownEmail).get("detail"));
    }

    @Test
    void passwordsAreStoredOnlyAsBcryptHashes() {
        Account student = registerStudent();

        String hash = jdbcTemplate.queryForObject(
                "select password_hash from users where email = ?", String.class, student.email());

        assertThat(hash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    void studentCannotManageTheCatalog() {
        var result = post("/api/categories", registerStudent().token(), """
                {"name": "Hacking %s"}""".formatted(unique()));

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void studentCannotSeeOrTouchAnotherStudentsEnrollment() {
        String courseId = createPublishedCourse(createInstructor(), 5, BigDecimal.TEN);
        Account owner = registerStudent();
        Account intruder = registerStudent();
        String enrollmentId = id(enroll(owner.token(), courseId, UUID.randomUUID().toString()));

        assertThat(get("/api/enrollments/" + enrollmentId, intruder.token())).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(post("/api/enrollments/" + enrollmentId + "/cancel", intruder.token(), null))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get("/api/students/" + owner.id() + "/enrollments", intruder.token()))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(post("/api/enrollments/" + enrollmentId + "/cancel", owner.token(), null))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
    }

    @Test
    void instructorManagesOnlyTheirOwnCourses() {
        Account owner = createInstructor();
        Account other = createInstructor();
        String courseId = createDraftCourse(owner, 5, BigDecimal.TEN);

        assertThat(post("/api/courses/" + courseId + "/publish", other.token(), null))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get("/api/courses/" + courseId + "/enrollments", other.token()))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(post("/api/courses", other.token(), """
                {"title": "Impersonated", "durationHours": 1, "level": "BEGINNER", "price": 1,
                 "capacity": 1, "categoryId": "%s", "instructorId": "%s"}""".formatted(createCategory(), owner.id())))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(post("/api/courses/" + courseId + "/publish", owner.token(), null)).hasStatusOk();
    }

    @Test
    void courseInstructorCanSeeEnrollmentsOfTheirCourse() {
        Account instructor = createInstructor();
        String courseId = createPublishedCourse(instructor, 5, BigDecimal.TEN);
        String enrollmentId = id(enroll(registerStudent().token(), courseId, UUID.randomUUID().toString()));

        assertThat(get("/api/enrollments/" + enrollmentId, instructor.token())).hasStatusOk();
    }

    @Test
    void studentsOnlySeePublishedCourses() {
        Account instructor = createInstructor();
        String draftId = createDraftCourse(instructor, 5, BigDecimal.TEN);
        String studentToken = registerStudent().token();

        assertThat(get("/api/courses/" + draftId, studentToken)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(get("/api/courses/" + draftId, instructor.token())).hasStatusOk();
        assertThat(get("/api/courses?status=DRAFT&size=100", studentToken))
                .bodyJson().extractingPath("$.content[*].status").asArray().doesNotContain("DRAFT");
    }

    @Test
    void onlyStudentsCanEnroll() {
        String courseId = createPublishedCourse(createInstructor(), 5, BigDecimal.TEN);

        assertThat(enroll(adminToken(), courseId, UUID.randomUUID().toString())).hasStatus(HttpStatus.FORBIDDEN);
    }
}

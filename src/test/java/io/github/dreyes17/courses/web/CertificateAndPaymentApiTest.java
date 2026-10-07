package io.github.dreyes17.courses.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** What a client sees of the payment and the certificate that the asynchronous flow produces for an enrollment. */
class CertificateAndPaymentApiTest extends ApiTestSupport {

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(15);

    @Test
    void completedEnrollmentHasACertificateItsPeopleCanReadAndAnyoneCanVerify() {
        Account instructor = createInstructor();
        String courseId = createPublishedCourse(instructor, 5, new BigDecimal("10.00"));
        String courseTitle = body(get("/api/courses/" + courseId, instructor.token())).get("title").asString();
        Account student = registerStudent();
        String enrollmentId = activeEnrollment(student, courseId);
        String certificateUri = "/api/enrollments/" + enrollmentId + "/certificate";

        assertThat(get(certificateUri, student.token())).as("not completed yet")
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("Certificate of enrollment " + enrollmentId + " not found");

        put("/api/enrollments/" + enrollmentId + "/progress", student.token(), """
                {"progress": 100}""");
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(get(certificateUri, student.token())).hasStatusOk());
        JsonNode certificate = body(get(certificateUri, student.token()));
        String code = certificate.get("code").asString();
        assertThat(code).startsWith("CERT-");
        assertThat(certificate.get("courseTitle").asString()).isEqualTo(courseTitle);
        assertThat(certificate.get("studentId").asString()).isEqualTo(student.id());

        assertThat(get(certificateUri, instructor.token())).as("the course's instructor").hasStatusOk();
        assertThat(get(certificateUri, adminToken())).hasStatusOk();
        assertThat(get(certificateUri, registerStudent().token())).as("another student")
                .hasStatus(HttpStatus.FORBIDDEN);

        var verification = get("/api/certificates/" + code, null);
        assertThat(verification).as("public: no token").hasStatusOk();
        JsonNode verified = body(verification);
        assertThat(verified.get("holderName").asString()).isEqualTo("Ada Lovelace");
        assertThat(verified.get("courseTitle").asString()).isEqualTo(courseTitle);
        assertThat(verified.propertyNames()).as("nothing beyond what the certificate itself shows")
                .containsExactlyInAnyOrder("code", "holderName", "courseTitle", "issuedAt");
    }

    @Test
    void unknownCertificateCodeIsNotFound() {
        var result = get("/api/certificates/CERT-0000000000000000", null);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void confirmedPaymentIsVisibleToItsStudent() {
        Account instructor = createInstructor();
        String courseId = createPublishedCourse(instructor, 5, new BigDecimal("10.00"));
        Account student = registerStudent();
        String enrollmentId = activeEnrollment(student, courseId);
        String paymentUri = "/api/enrollments/" + enrollmentId + "/payment";

        JsonNode payment = body(get(paymentUri, student.token()));
        assertThat(payment.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(payment.get("enrollmentId").asString()).isEqualTo(enrollmentId);
        assertThat(payment.get("amount").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(payment.get("currency").asString()).isEqualTo("EUR");
        assertThat(payment.get("failureReason").isNull()).isTrue();

        assertThat(get(paymentUri, adminToken())).hasStatusOk();
        assertThat(get(paymentUri, instructor.token())).as("payments are only for the student and ADMIN")
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get(paymentUri, registerStudent().token())).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void declinedPaymentTellsTheStudentWhyTheEnrollmentWasCancelled() {
        // Above app.payments.simulation.decline-above, so the simulated gateway declines it.
        String courseId = createPublishedCourse(createInstructor(), 5, new BigDecimal("20000.00"));
        Account student = registerStudent();
        String enrollmentId = id(enroll(student.token(), courseId, UUID.randomUUID().toString()));

        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(get("/api/enrollments/" + enrollmentId, student.token()))
                        .bodyJson().extractingPath("$.status").isEqualTo("CANCELLED"));
        JsonNode payment = body(get("/api/enrollments/" + enrollmentId + "/payment", student.token()));
        assertThat(payment.get("status").asString()).isEqualTo("FAILED");
        assertThat(payment.get("failureReason").asString()).contains("exceeds the simulated limit");
    }

    @Test
    void paymentAndCertificateOfAnUnknownEnrollmentAreNotFound() {
        String unknown = UUID.randomUUID().toString();

        assertThat(get("/api/enrollments/" + unknown + "/payment", adminToken()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Enrollment " + unknown + " not found");
        assertThat(get("/api/enrollments/" + unknown + "/certificate", adminToken()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("Enrollment " + unknown + " not found");
    }

    private String activeEnrollment(Account student, String courseId) {
        String enrollmentId = id(enroll(student.token(), courseId, UUID.randomUUID().toString()));
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(get("/api/enrollments/" + enrollmentId, student.token()))
                        .bodyJson().extractingPath("$.status").isEqualTo("ACTIVE"));
        return enrollmentId;
    }
}

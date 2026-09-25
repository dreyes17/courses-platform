package com.example.courses.web;

import com.example.courses.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.UUID;

abstract class ApiTestSupport extends AbstractIntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvcTester mvc;
    @Autowired
    protected JsonMapper jsonMapper;

    /** A user who logged in through the API: their profile id (student or instructor) and bearer token. */
    protected record Account(String id, String email, String token) {
    }

    protected String adminToken() {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected String login(String email, String password) {
        var result = post("/api/auth/token", null, """
                {"email": "%s", "password": "%s"}""".formatted(email, password));
        return body(result).get("accessToken").asString();
    }

    protected Account registerStudent() {
        String email = "student-" + unique() + "@learn.test";
        var result = post("/api/auth/register", null, """
                {"firstName": "Ada", "lastName": "Lovelace", "email": "%s", "password": "%s"}"""
                .formatted(email, PASSWORD));
        return new Account(id(result), email, login(email, PASSWORD));
    }

    protected Account createInstructor() {
        String email = "instructor-" + unique() + "@teach.test";
        var result = post("/api/instructors", adminToken(), """
                {"name": "Grace Hopper", "email": "%s", "password": "%s"}""".formatted(email, PASSWORD));
        return new Account(id(result), email, login(email, PASSWORD));
    }

    protected String createCategory() {
        return id(post("/api/categories", adminToken(), """
                {"name": "Category %s"}""".formatted(unique())));
    }

    protected String createDraftCourse(Account instructor, int capacity, BigDecimal price) {
        return id(post("/api/courses", instructor.token(), """
                {"title": "Course %s", "durationHours": 10, "level": "BEGINNER", "price": %s,
                 "capacity": %d, "categoryId": "%s", "instructorId": "%s"}"""
                .formatted(unique(), price.toPlainString(), capacity, createCategory(), instructor.id())));
    }

    protected String createPublishedCourse(Account instructor, int capacity, BigDecimal price) {
        String courseId = createDraftCourse(instructor, capacity, price);
        post("/api/courses/" + courseId + "/publish", instructor.token(), null);
        return courseId;
    }

    protected MvcTestResult post(String uri, String token, String json) {
        return send(mvc.post().uri(uri), token, json);
    }

    protected MvcTestResult put(String uri, String token, String json) {
        return send(mvc.put().uri(uri), token, json);
    }

    protected MvcTestResult get(String uri, String token) {
        return send(mvc.get().uri(uri), token, null);
    }

    protected MvcTestResult enroll(String token, String courseId, String idempotencyKey) {
        return send(mvc.post().uri("/api/enrollments").header("Idempotency-Key", idempotencyKey), token, """
                {"courseId": "%s"}""".formatted(courseId));
    }

    private static MvcTestResult send(MockMvcRequestBuilder request, String token, String json) {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return request.exchange();
    }

    protected JsonNode body(MvcTestResult result) {
        try {
            return jsonMapper.readTree(result.getResponse().getContentAsString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    protected String id(MvcTestResult result) {
        return body(result).get("id").asString();
    }

    protected static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

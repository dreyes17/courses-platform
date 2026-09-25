package com.example.courses.web;

import com.example.courses.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

abstract class ApiTestSupport extends AbstractIntegrationTest {

    @Autowired
    protected MockMvcTester mvc;
    @Autowired
    protected JsonMapper jsonMapper;

    protected MvcTestResult post(String uri, String json) {
        var request = mvc.post().uri(uri);
        return json == null ? request.exchange() : request.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected MvcTestResult put(String uri, String json) {
        return mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected MvcTestResult get(String uri) {
        return mvc.get().uri(uri).exchange();
    }

    protected MvcTestResult enroll(String studentId, String courseId, String idempotencyKey) {
        return mvc.post().uri("/api/enrollments")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"studentId": "%s", "courseId": "%s"}""".formatted(studentId, courseId))
                .exchange();
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

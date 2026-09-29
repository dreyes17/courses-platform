package com.example.courses.web;

import com.example.courses.support.RealServerTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Needs real, separate ports, so unlike the other web tests it starts actual servers instead of MockMvc. */
@RealServerTest
class ManagementPortTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int apiPort;
    @LocalManagementPort
    private int managementPort;

    @Test
    void prometheusMetricsAreReadableOnTheManagementPortWithoutCredentials() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_memory_used_bytes");
    }

    @Test
    void healthOnTheManagementPortReportsDatabaseAndBroker() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"db\"", "\"rabbit\"");
    }

    @Test
    void readinessDependsOnTheDatabaseAndTheBroker() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/health/readiness");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"db\"", "\"rabbit\"", "\"readinessState\"");
    }

    @Test
    void actuatorIsNotServedOnTheApiPort() throws Exception {
        assertThat(get(apiPort, "/actuator/prometheus").statusCode()).isNotEqualTo(200);
        assertThat(get(apiPort, "/actuator/health").statusCode()).isNotEqualTo(200);
    }

    @Test
    void theApiStaysProtectedAndIsNotReachableThroughTheManagementPort() throws Exception {
        assertThat(get(apiPort, "/api/courses").statusCode()).isEqualTo(401);
        assertThat(get(managementPort, "/api/courses").statusCode()).isNotEqualTo(200);
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

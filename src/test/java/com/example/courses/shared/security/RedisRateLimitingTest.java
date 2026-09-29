package com.example.courses.shared.security;

import com.example.courses.shared.security.RateLimitProperties.Limit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The rate-limit store on its own: shared by every instance, and never able to take the API down. */
@Testcontainers
class RedisRateLimitingTest {

    @Container
    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:8.10-alpine")).withExposedPorts(6379);

    private static final BucketConfiguration THREE_PER_MINUTE = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder().capacity(3).refillGreedy(3, Duration.ofMinutes(1)).build()).build();

    private final List<RedisBuckets> opened = new ArrayList<>();

    @AfterEach
    void close() {
        opened.forEach(RedisBuckets::destroy);
    }

    @Test
    void everyInstanceSharesTheSameBudget() {
        // Two independent clients, as two instances of the application would have.
        RedisBuckets instanceA = buckets(redis.getHost(), redis.getMappedPort(6379));
        RedisBuckets instanceB = buckets(redis.getHost(), redis.getMappedPort(6379));
        String client = "login:ip:" + UUID.randomUUID();

        assertThat(instanceA.tryConsume(client, THREE_PER_MINUTE).isConsumed()).isTrue();
        assertThat(instanceB.tryConsume(client, THREE_PER_MINUTE).isConsumed()).isTrue();
        assertThat(instanceA.tryConsume(client, THREE_PER_MINUTE).isConsumed()).isTrue();

        assertThat(instanceB.tryConsume(client, THREE_PER_MINUTE).isConsumed()).isFalse();
        assertThat(instanceA.tryConsume(client, THREE_PER_MINUTE).isConsumed()).isFalse();
    }

    @Test
    void anUnreachableRedisFailsFastInsteadOfBlockingTheRequest() throws Exception {
        RedisBuckets unreachable = buckets("localhost", freePort());

        long start = System.nanoTime();
        assertThatThrownBy(() -> unreachable.tryConsume("login:ip:1.2.3.4", THREE_PER_MINUTE))
                .isInstanceOf(RuntimeException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void withoutRedisRequestsAreLetThroughAndCounted() throws Exception {
        var registry = new SimpleMeterRegistry();
        var filter = new RateLimitFilter(properties(), buckets("localhost", freePort()),
                new ProblemDetailsSecurityHandler(JsonMapper.builder().build()), registry);
        var request = new MockHttpServletRequest("POST", "/api/auth/token");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).as("the request reached the application").isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(registry.get("courses.rate_limit.unavailable").counter().count()).isEqualTo(1);
    }

    private RedisBuckets buckets(String host, int port) {
        DataRedisConnectionDetails details = new DataRedisConnectionDetails() {
            @Override
            public Standalone getStandalone() {
                return Standalone.of(host, port);
            }
        };
        RedisBuckets buckets = new RedisBuckets(details, properties());
        opened.add(buckets);
        return buckets;
    }

    private static RateLimitProperties properties() {
        Limit limit = new Limit(3, Duration.ofMinutes(1));
        return new RateLimitProperties(true, Duration.ofMillis(200), limit, limit, limit, limit);
    }

    private static int freePort() throws Exception {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}

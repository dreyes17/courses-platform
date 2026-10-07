package io.github.dreyes17.courses.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Per-client request limits for the endpoints worth abusing. Each limit is a token bucket: up to
 * {@code capacity} requests in a burst, refilled at {@code capacity} per {@code period}. {@code redisTimeout}
 * bounds each check against Redis, where the buckets are stored.
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, Duration redisTimeout, Limit login, Limit registration,
                                  Limit enrollment, Limit mcp) {

    public record Limit(int capacity, Duration period) {
    }
}

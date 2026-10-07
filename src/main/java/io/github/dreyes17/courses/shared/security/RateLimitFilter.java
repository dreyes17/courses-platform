package io.github.dreyes17.courses.shared.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.dreyes17.courses.shared.security.RateLimitProperties.Limit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Token-bucket rate limits on the endpoints an attacker or a runaway client would hammer: login (password
 * guessing), registration (account spam), enrollment (a client retrying in a loop) and MCP tool calls (an AI agent
 * stuck in a loop; this also covers enroll_student, which doesn't go through /api/enrollments). Login and
 * registration are anonymous, so they are limited per client IP; the others per user, so students behind the same
 * NAT don't share a budget. Runs after bearer-token authentication, which is what identifies the user.
 * <p>
 * Buckets live in Redis ({@link RedisBuckets}), so the limits hold across every instance. If Redis can't be
 * reached, requests are let through (fail-open): an outage of this protection shouldn't take the API down with it.
 * Each such request is counted in courses.rate_limit.unavailable, which Prometheus alerts on.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private record Rule(String name, HttpMethod method, String path, Limit limit, boolean perUser) {

        boolean matches(HttpServletRequest request) {
            return method.matches(request.getMethod()) && path.equals(request.getRequestURI());
        }

        BucketConfiguration bucket() {
            return BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder().capacity(limit.capacity())
                            .refillGreedy(limit.capacity(), limit.period()).build())
                    .build();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Duration UNAVAILABLE_LOG_INTERVAL = Duration.ofMinutes(1);

    private final List<Rule> rules;
    private final RedisBuckets buckets;
    private final Map<String, Counter> rejections;
    private final Counter unavailable;
    private final ProblemDetailsSecurityHandler problems;
    private final AtomicReference<Instant> lastUnavailableLog = new AtomicReference<>(Instant.MIN);

    RateLimitFilter(RateLimitProperties properties, RedisBuckets buckets, ProblemDetailsSecurityHandler problems,
                    MeterRegistry registry) {
        this.rules = List.of(
                new Rule("login", HttpMethod.POST, "/api/auth/token", properties.login(), false),
                new Rule("registration", HttpMethod.POST, "/api/auth/register", properties.registration(), false),
                new Rule("enrollment", HttpMethod.POST, "/api/enrollments", properties.enrollment(), true),
                new Rule("mcp", HttpMethod.POST, "/mcp", properties.mcp(), true));
        this.buckets = buckets;
        this.rejections = rules.stream().collect(Collectors.toMap(Rule::name, rule -> Counter
                .builder("courses.rate_limit.rejected").description("Requests rejected with 429 by rate limiting")
                .tag("rule", rule.name()).register(registry), (a, b) -> a));
        this.unavailable = Counter.builder("courses.rate_limit.unavailable")
                .description("Requests let through unchecked because the rate-limit store (Redis) was unreachable")
                .register(registry);
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = rules.stream().filter(candidate -> candidate.matches(request)).findFirst().orElse(null);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }
        ConsumptionProbe probe;
        try {
            probe = buckets.tryConsume(rule.name() + ':' + clientKey(rule, request), rule.bucket());
        } catch (RuntimeException e) {
            unavailable.increment();
            logUnavailable(e);
            chain.doFilter(request, response);
            return;
        }
        if (probe.isConsumed()) {
            response.setHeader(REMAINING_HEADER, Long.toString(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1);
        rejections.get(rule.name()).increment();
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setHeader(REMAINING_HEADER, "0");
        problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
                "Rate limit exceeded for this operation; retry in %d seconds".formatted(retryAfterSeconds));
    }

    /** At most once a minute, so a Redis outage doesn't flood the logs with one line per request. */
    private void logUnavailable(RuntimeException e) {
        Instant last = lastUnavailableLog.get();
        Instant now = Instant.now();
        if (now.isAfter(last.plus(UNAVAILABLE_LOG_INTERVAL)) && lastUnavailableLog.compareAndSet(last, now)) {
            log.warn("Rate limiting unavailable, letting requests through: {}", e.toString());
        }
    }

    /**
     * The remote address is the direct peer. Behind a reverse proxy, enable server.forward-headers-strategy so
     * it becomes the real client; X-Forwarded-For is never read here, because any client can forge it.
     */
    private static String clientKey(Rule rule, HttpServletRequest request) {
        if (rule.perUser()) {
            CurrentUser user = CurrentUser.from(SecurityContextHolder.getContext().getAuthentication());
            if (user != null) {
                return "user:" + user.userId();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}

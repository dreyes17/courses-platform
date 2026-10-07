package io.github.dreyes17.courses.shared.security;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Rate-limit buckets stored in Redis, so every instance of the application shares one budget per client instead
 * of each counting on its own. Bucket4j updates a bucket with compare-and-swap, so concurrent requests on
 * different instances never lose a consumption.
 * <p>
 * Built for a check that runs on every limited request: short timeouts, commands rejected at once while
 * disconnected instead of queued, and a lazy connection that is retried at most every few seconds. Any failure is
 * thrown to {@link RateLimitFilter}, which then lets the request through: the application starts and keeps
 * serving without Redis, only unlimited.
 */
@Component
class RedisBuckets implements DisposableBean {

    static final String KEY_PREFIX = "rate-limit:";
    private static final Duration RECONNECT_BACKOFF = Duration.ofSeconds(5);

    private final RedisClient client;
    private final Duration timeout;
    private final ReentrantLock connecting = new ReentrantLock();
    private volatile StatefulRedisConnection<String, byte[]> connection;
    private volatile LettuceBasedProxyManager<String> buckets;
    private volatile Instant nextConnectAttempt = Instant.MIN;

    RedisBuckets(DataRedisConnectionDetails redis, RateLimitProperties properties) {
        this.timeout = properties.redisTimeout();
        var standalone = redis.getStandalone();
        var uri = RedisURI.builder()
                .withHost(standalone.getHost())
                .withPort(standalone.getPort())
                .withDatabase(standalone.getDatabase())
                .withTimeout(timeout);
        if (StringUtils.hasText(redis.getPassword())) {
            if (StringUtils.hasText(redis.getUsername())) {
                uri.withAuthentication(redis.getUsername(), redis.getPassword());
            } else {
                uri.withPassword(redis.getPassword().toCharArray());
            }
        }
        this.client = RedisClient.create(uri.build());
        this.client.setOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder().connectTimeout(timeout).build())
                .build());
    }

    /** Takes one token from the client's bucket, creating it with {@code configuration} on first use. */
    ConsumptionProbe tryConsume(String key, BucketConfiguration configuration) {
        return buckets().builder().build(KEY_PREFIX + key, () -> configuration).tryConsumeAndReturnRemaining(1);
    }

    private LettuceBasedProxyManager<String> buckets() {
        LettuceBasedProxyManager<String> current = buckets;
        if (current != null) {
            return current;
        }
        // A lock rather than synchronized: connecting blocks, and on Java 21 a virtual thread blocked inside
        // synchronized would pin its carrier thread.
        connecting.lock();
        try {
            if (buckets == null) {
                if (Instant.now().isBefore(nextConnectAttempt)) {
                    throw new IllegalStateException("Redis unavailable; next connection attempt at "
                            + nextConnectAttempt);
                }
                try {
                    connection = client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                } catch (RuntimeException e) {
                    nextConnectAttempt = Instant.now().plus(RECONNECT_BACKOFF);
                    throw e;
                }
                buckets = Bucket4jLettuce.casBasedBuilder(connection)
                        .requestTimeout(timeout)
                        // A bucket's key expires once it would have refilled completely: an idle client costs
                        // nothing in Redis, and losing the key loses nothing.
                        .expirationAfterWrite(ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                        .build();
            }
            return buckets;
        } finally {
            connecting.unlock();
        }
    }

    @Override
    public void destroy() {
        if (connection != null) {
            connection.close();
        }
        client.shutdown();
    }
}

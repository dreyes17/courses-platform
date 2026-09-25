package com.example.courses.idempotency.application;

import com.example.courses.idempotency.domain.IdempotencyKey;
import com.example.courses.idempotency.repository.IdempotencyKeyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs an action at most once per Idempotency-Key. The key is claimed and completed in the caller's
 * transaction, so a failed action leaves no trace and the client can retry with the same key.
 */
@Component
public class IdempotentRequests {

    private final IdempotencyKeyRepository keys;

    public IdempotentRequests(IdempotencyKeyRepository keys) {
        this.keys = keys;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T execute(String key, String endpoint, String requestFingerprint, int successStatus,
                         Supplier<T> action, Function<T, String> toResponseBody, Function<String, T> replay) {
        String requestHash = sha256(requestFingerprint);
        if (keys.claim(key, endpoint, requestHash) == 0) {
            IdempotencyKey existing = keys.findById(key).orElseThrow();
            if (!existing.matchesRequest(requestHash)) {
                throw new IdempotencyKeyReusedException(key);
            }
            return replay.apply(existing.getResponseBody());
        }
        T result = action.get();
        keys.findById(key).orElseThrow().complete(successStatus, toResponseBody.apply(result));
        return result;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}

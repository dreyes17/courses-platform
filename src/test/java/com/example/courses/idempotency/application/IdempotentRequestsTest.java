package com.example.courses.idempotency.application;

import com.example.courses.idempotency.domain.IdempotencyKey;
import com.example.courses.idempotency.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentRequestsTest {

    private static final String KEY = "key-1";
    private static final String ENDPOINT = "POST /things";

    @Mock
    private IdempotencyKeyRepository keys;
    @InjectMocks
    private IdempotentRequests requests;

    private final AtomicInteger actionRuns = new AtomicInteger();

    @Test
    void firstRequestRunsTheActionAndStoresItsResponse() {
        var claimedHash = new AtomicReference<String>();
        when(keys.claim(eq(KEY), eq(ENDPOINT), any())).thenAnswer(invocation -> {
            claimedHash.set(invocation.getArgument(2));
            return 1;
        });
        var stored = new AtomicReference<IdempotencyKey>();
        when(keys.findById(KEY)).thenAnswer(invocation -> {
            stored.set(IdempotencyKey.open(KEY, ENDPOINT, claimedHash.get()));
            return Optional.of(stored.get());
        });

        String result = execute("request-A");

        assertThat(result).isEqualTo("created-1");
        assertThat(actionRuns).hasValue(1);
        assertThat(claimedHash.get()).as("only a hash of the request is stored").isEqualTo(sha256("request-A"));
        assertThat(stored.get().getResponseStatus()).isEqualTo(201);
        assertThat(stored.get().getResponseBody()).isEqualTo("created-1");
    }

    @Test
    void retryOfTheSameRequestReplaysTheStoredResponseWithoutRunningTheActionAgain() {
        when(keys.claim(eq(KEY), eq(ENDPOINT), any())).thenReturn(0);
        IdempotencyKey existing = IdempotencyKey.open(KEY, ENDPOINT, sha256("request-A"));
        existing.complete(201, "created-1");
        when(keys.findById(KEY)).thenReturn(Optional.of(existing));

        String result = execute("request-A");

        assertThat(result).isEqualTo("replayed:created-1");
        assertThat(actionRuns).hasValue(0);
    }

    @Test
    void reusingTheKeyForADifferentRequestIsRejected() {
        when(keys.claim(eq(KEY), eq(ENDPOINT), any())).thenReturn(0);
        IdempotencyKey existing = IdempotencyKey.open(KEY, ENDPOINT, sha256("request-A"));
        existing.complete(201, "created-1");
        when(keys.findById(KEY)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> execute("request-B")).isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(actionRuns).hasValue(0);
    }

    private String execute(String fingerprint) {
        return requests.execute(KEY, ENDPOINT, fingerprint, 201,
                () -> "created-" + actionRuns.incrementAndGet(),
                result -> result,
                body -> "replayed:" + body);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

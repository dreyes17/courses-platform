package io.github.dreyes17.courses.idempotency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "key", nullable = false, updatable = false, length = 100)
    private String key;

    @Column(nullable = false, updatable = false, length = 100)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKey() {
    }

    private IdempotencyKey(String key, String endpoint, String requestHash) {
        this.key = Objects.requireNonNull(key, "key");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        this.createdAt = Instant.now();
    }

    public static IdempotencyKey open(String key, String endpoint, String requestHash) {
        return new IdempotencyKey(key, endpoint, requestHash);
    }

    public boolean matchesRequest(String requestHash) {
        return this.requestHash.equals(requestHash);
    }

    public boolean isCompleted() {
        return responseStatus != null;
    }

    public void complete(int status, String body) {
        if (isCompleted()) {
            throw new IllegalStateException("Idempotency key %s was already completed".formatted(key));
        }
        this.responseStatus = status;
        this.responseBody = body;
    }

    public String getKey() {
        return key;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

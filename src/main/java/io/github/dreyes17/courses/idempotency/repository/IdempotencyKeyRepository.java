package io.github.dreyes17.courses.idempotency.repository;

import io.github.dreyes17.courses.idempotency.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    /**
     * Returns 1 if this transaction now owns the key. A concurrent request with the same key blocks
     * here until the owner commits (then gets 0 and replays) or rolls back (then gets 1 and runs).
     */
    @Modifying
    @Query(value = """
            insert into idempotency_keys (key, endpoint, request_hash, created_at)
            values (:key, :endpoint, :requestHash, now())
            on conflict (key) do nothing
            """, nativeQuery = true)
    int claim(@Param("key") String key, @Param("endpoint") String endpoint,
              @Param("requestHash") String requestHash);
}

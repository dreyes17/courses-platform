package com.example.courses.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** SKIP LOCKED lets several app instances run the relay without publishing the same rows concurrently. */
    @Query(value = """
            select * from outbox_events
             where status = 'PENDING'
             order by created_at
             limit :limit
               for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockNextPending(@Param("limit") int limit);

    long countByStatus(OutboxStatus status);
}

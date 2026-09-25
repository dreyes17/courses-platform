package com.example.courses.messaging.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    /**
     * Returns 1 for the first delivery and 0 for a duplicate. A concurrent duplicate blocks on the
     * uncommitted row and gets 0 once the first delivery commits (or 1 if it rolled back).
     */
    @Modifying
    @Query(value = """
            insert into processed_events (event_id, consumer_name, processed_at)
            values (:eventId, :consumerName, now())
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("consumerName") String consumerName);
}

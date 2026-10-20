package com.epam.java.specialization.streamingservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Locks the oldest unsent events; another relay instance skips them instead of sending them too. */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE sent_at IS NULL
            ORDER BY created_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockUnsent(@Param("limit") int limit);

    long countBySentAtIsNull();
}

package com.epam.java.specialization.authservice.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_unpublished", columnList = "publishedAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    @Column(length = 64)
    private String traceparent;

    static OutboxEvent pending(UUID id, String topic, String messageKey, String payload, Instant createdAt,
                               String traceparent) {
        OutboxEvent event = new OutboxEvent();
        event.id = id;
        event.topic = topic;
        event.messageKey = messageKey;
        event.payload = payload;
        event.createdAt = createdAt;
        event.traceparent = traceparent;
        return event;
    }

    void markPublished(Instant at) {
        this.publishedAt = at;
    }
}

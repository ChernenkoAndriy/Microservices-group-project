package com.epam.java.specialization.streamingservice.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An event waiting to be sent to Kafka. It is written in the same transaction as the change it describes,
 * so both are committed or neither is; {@link OutboxRelay} sends it afterwards.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    /** The {@code eventId} of the payload. */
    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String aggregateType;

    @Column(nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(nullable = false, length = 100)
    private String messageKey;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 500)
    private String lastError;

    /** W3C {@code traceparent} of the change, so the Kafka record continues its trace. */
    @Column(length = 64)
    private String traceparent;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateType, String aggregateId, String topic, String messageKey,
                       String eventType, String payload, Instant createdAt, String traceparent) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.traceparent = traceparent;
    }

    public void markSent(Instant at) {
        this.sentAt = at;
        this.attempts++;
        this.lastError = null;
    }

    public void markFailedAttempt(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public String getTraceparent() { return traceparent; }
}

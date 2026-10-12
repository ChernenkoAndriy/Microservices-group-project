package com.epam.java.specialization.trackservice.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** An event this service has already applied; its id is the {@code eventId} from the payload. */
@Entity
@Table(name = "processed_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(nullable = false)
    private Instant processedAt;
}

package com.epam.java.specialization.streamingservice.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/** Adds an event to the outbox inside the caller's transaction; there is no outbox write without one. */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    public OutboxWriter(OutboxEventRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID eventId, String aggregateType, Object aggregateId, String topic, String eventType,
                       Object payload) {
        String key = String.valueOf(aggregateId);
        repository.save(new OutboxEvent(eventId, aggregateType, key, topic, key, eventType,
                jsonMapper.writeValueAsString(payload), Instant.now()));
    }
}

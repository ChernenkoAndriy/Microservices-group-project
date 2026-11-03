package com.epam.java.specialization.streamingservice.outbox;

import com.epam.java.specialization.streamingservice.tracing.TraceContextCarrier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Adds an event to the outbox inside the caller's transaction; there is no outbox write without one.
 * The row keeps the caller's {@code traceparent}, so {@link OutboxRelay} sends the event in the same trace.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;
    private final TraceContextCarrier traceContext;

    public OutboxWriter(OutboxEventRepository repository, JsonMapper jsonMapper, TraceContextCarrier traceContext) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.traceContext = traceContext;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID eventId, String aggregateType, Object aggregateId, String topic, String eventType,
                       Object payload) {
        String key = String.valueOf(aggregateId);
        repository.save(new OutboxEvent(eventId, aggregateType, key, topic, key, eventType,
                jsonMapper.writeValueAsString(payload), Instant.now(), traceContext.currentTraceparent()));
    }
}

package com.epam.java.specialization.streamingservice.outbox;

import com.epam.java.specialization.streamingservice.tracing.TraceContextCarrier;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends unsent outbox events to Kafka, oldest first, and marks them sent. Delivery is at least once:
 * a crash after the send but before the commit sends the event again, and consumers skip the repeat
 * by its {@code eventId}. When a send fails the batch stops, so events of one key keep their order.
 * Each event is sent in a span that continues the trace stored with it, and the observed
 * {@code KafkaTemplate} puts that trace's {@code traceparent} into the record headers.
 */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    public static final String EVENT_TYPE_HEADER = "eventType";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TraceContextCarrier traceContext;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                       TraceContextCarrier traceContext,
                       @Value("${outbox.relay.batch-size:50}") int batchSize,
                       @Value("${outbox.relay.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.traceContext = traceContext;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:1000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = repository.lockUnsent(batchSize);
        for (OutboxEvent event : batch) {
            if (!send(event)) {
                return;
            }
        }
    }

    /** Sends one event in its own trace; every attempt, failed ones included, is a span of that trace. */
    private boolean send(OutboxEvent event) {
        Span span = traceContext.startSpan("outbox send", event.getTraceparent())
                .tag("outbox.event.id", event.getId().toString())
                .tag("outbox.event.type", event.getEventType())
                .tag("messaging.destination.name", event.getTopic());
        // The scope encloses the catch blocks too (a try-with-resources resource is closed before its catch runs),
        // so the failure log line carries the traceId and shows up under "Logs for this span".
        try (Tracer.SpanInScope scope = traceContext.tracer().withSpan(span)) {
            try {
                kafkaTemplate.send(toRecord(event)).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                event.markSent(Instant.now());
                log.info("Sent outbox event id={} type={} topic={} key={}",
                        event.getId(), event.getEventType(), event.getTopic(), event.getMessageKey());
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.markFailedAttempt("Interrupted");
                span.error(e);
                return false;
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                event.markFailedAttempt(cause.toString());
                span.error(cause);
                log.warn("Could not send outbox event id={} (attempt {}), will retry: {}",
                        event.getId(), event.getAttempts(), cause.toString());
                return false;
            }
        } finally {
            span.end();
        }
    }

    private static ProducerRecord<String, String> toRecord(OutboxEvent event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
        record.headers().add(EVENT_TYPE_HEADER, event.getEventType().getBytes(StandardCharsets.UTF_8));
        return record;
    }
}

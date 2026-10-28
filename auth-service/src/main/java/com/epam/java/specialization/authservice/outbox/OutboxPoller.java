package com.epam.java.specialization.authservice.outbox;

import com.epam.java.specialization.authservice.tracing.TraceContextCarrier;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final TraceContextCarrier traceContext;

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    public void poll() {
        repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()
                .forEach(this::publish);
    }

    private void publish(OutboxEvent event) {
        Span span = traceContext.startSpan("outbox send", event.getTraceparent())
                .tag("outbox.event.id", event.getId().toString())
                .tag("messaging.destination.name", event.getTopic());
        // The scope encloses the catch block too (a try-with-resources resource is closed before its catch runs),
        // so the failure log line carries the traceId and shows up under "Logs for this span".
        try (Tracer.SpanInScope scope = traceContext.tracer().withSpan(span)) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                event.markPublished(clock.instant());
                repository.save(event);
                log.debug("Published outbox event id={} topic={}", event.getId(), event.getTopic());
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                span.error(e);
                log.warn("Failed to publish outbox event id={} topic={}", event.getId(), event.getTopic(), e);
            }
        } finally {
            span.end();
        }
    }
}

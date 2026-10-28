package com.epam.java.specialization.authservice.outbox;

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

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    public void poll() {
        repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()
                .forEach(this::publish);
    }

    private void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                    .get(10, TimeUnit.SECONDS);
            event.markPublished(clock.instant());
            repository.save(event);
            log.debug("Published outbox event id={} topic={}", event.getId(), event.getTopic());
        } catch (Exception e) {
            log.warn("Failed to publish outbox event id={} topic={}", event.getId(), event.getTopic(), e);
        }
    }
}

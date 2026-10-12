package com.epam.java.specialization.trackservice.messaging;

import com.epam.java.specialization.trackservice.event.AssetReadySnapshot;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consumes {@code streaming.asset.ready}. A failed event is retried through the retry topics with a growing
 * delay, so it does not hold up the events behind it; after the last attempt it goes to the dead letter topic.
 * An event that can never succeed (unreadable, invalid, unknown track) goes to the dead letter topic at once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetReadyListener {

    private final AssetReadyHandler handler;
    private final JsonMapper jsonMapper;
    private final Validator validator;

    @RetryableTopic(
            attempts = "${events.consumer.retry.attempts:4}",
            backOff = @BackOff(
                    delayString = "${events.consumer.retry.delay-ms:1000}",
                    multiplierString = "${events.consumer.retry.multiplier:2}",
                    maxDelayString = "${events.consumer.retry.max-delay-ms:10000}"),
            retryTopicSuffix = "-retry",
            dltTopicSuffix = "-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = NonRetryableEventException.class,
            numPartitions = "${events.topics.partitions:3}",
            replicationFactor = "1")
    @KafkaListener(topics = "${events.topics.asset-ready}", groupId = "${spring.kafka.consumer.group-id}")
    public void onAssetReady(ConsumerRecord<String, String> record) {
        log.info("Received {} key={} partition={} offset={}",
                record.topic(), record.key(), record.partition(), record.offset());
        handler.handle(parse(record.value()), record.topic());
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record,
                             @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) byte[] error,
                             @Header(name = KafkaHeaders.ORIGINAL_TOPIC, required = false) byte[] originalTopic) {
        log.error("Dead letter from {} key={} offset={} error={} payload={}",
                text(originalTopic), record.key(), record.offset(), text(error), record.value());
    }

    private static String text(byte[] header) {
        return header == null ? null : new String(header, StandardCharsets.UTF_8);
    }

    private AssetReadySnapshot parse(String payload) {
        AssetReadySnapshot event;
        try {
            event = jsonMapper.readValue(payload, AssetReadySnapshot.class);
        } catch (JacksonException e) {
            throw new NonRetryableEventException("Unreadable AssetReadySnapshot: " + e.getOriginalMessage(), e);
        }
        if (event == null) {
            throw new NonRetryableEventException("Empty AssetReadySnapshot");
        }
        Set<ConstraintViolation<AssetReadySnapshot>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new NonRetryableEventException("Invalid AssetReadySnapshot: " + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", ")));
        }
        return event;
    }
}

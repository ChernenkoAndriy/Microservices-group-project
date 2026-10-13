package com.epam.java.specialization.trackservice.messaging;

import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import com.epam.java.specialization.trackservice.repository.TrackRepository;
import com.epam.java.specialization.trackservice.service.TrackService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Runs the {@code streaming.asset.ready} consumer against an embedded Kafka broker: retries through the retry
 * topics, the dead letter topic, and the Idempotent Consumer.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-kafka;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true",
        "events.topics.partitions=1",
        "events.consumer.retry.delay-ms=100",
        "events.consumer.retry.multiplier=2",
        "events.consumer.retry.max-delay-ms=1000",
})
@EmbeddedKafka(partitions = 1)
class AssetReadyKafkaIntegrationTest {

    private static final String TOPIC = "streaming.asset.ready";
    private static final String DLT = TOPIC + "-dlt";
    private static final List<String> RETRY_TOPICS = List.of(TOPIC + "-retry-0", TOPIC + "-retry-1", TOPIC + "-retry-2");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final AtomicLong UNKNOWN_TRACK_IDS = new AtomicLong(900_000);

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @MockitoSpyBean
    private TrackService trackService;

    @Test
    void eventIsAppliedAndTheTrackIsPublished() {
        Long trackId = awaitingAudioTrack();
        UUID eventId = UUID.randomUUID();

        send(trackId, event(eventId, trackId, 245));

        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(eventId));
        Track track = trackRepository.findById(trackId).orElseThrow();
        assertThat(track.getStatus()).isEqualTo(TrackStatus.PUBLISHED);
        assertThat(track.getDurationSeconds()).isEqualTo(245);
        assertThat(track.getPublishedAt()).isNotNull();
    }

    @Test
    void duplicateDeliveriesChangeNothing() {
        Long trackId = awaitingAudioTrack();
        UUID eventId = UUID.randomUUID();
        String payload = event(eventId, trackId, 180);

        send(trackId, payload);
        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(eventId));
        Instant publishedAt = trackRepository.findById(trackId).orElseThrow().getPublishedAt();

        // The same event delivered again, as after a crash before the offset commit or an outbox resend.
        send(trackId, payload);
        send(trackId, payload);
        // One partition keeps the order: once this marker is applied, both copies above were consumed.
        Long markerTrackId = awaitingAudioTrack();
        UUID markerEventId = UUID.randomUUID();
        send(markerTrackId, event(markerEventId, markerTrackId, 60));
        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(markerEventId));

        verify(trackService, times(1)).publish(eq(trackId), anyInt());
        assertThat(processedEventRepository.findAll())
                .filteredOn(processed -> processed.getEventId().equals(eventId))
                .hasSize(1);
        assertThat(trackRepository.findById(trackId).orElseThrow().getPublishedAt()).isEqualTo(publishedAt);
        assertThat(dltRecordWithKey(trackId, Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void transientFailureIsRetriedAndAppliedOnce() {
        Long trackId = awaitingAudioTrack();
        UUID eventId = UUID.randomUUID();
        doThrow(new TransientDataAccessResourceException("catalog-db is down"))
                .doCallRealMethod()
                .when(trackService).publish(eq(trackId), anyInt());

        send(trackId, event(eventId, trackId, 200));

        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(eventId));
        verify(trackService, times(2)).publish(eq(trackId), anyInt());
        assertThat(trackRepository.findById(trackId).orElseThrow().getStatus()).isEqualTo(TrackStatus.PUBLISHED);
        assertThat(records(RETRY_TOPICS.getFirst(), String.valueOf(trackId), TIMEOUT)).hasSize(1);
        assertThat(dltRecordWithKey(trackId, Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void eventThatKeepsFailingGoesThroughEveryRetryTopicToTheDeadLetterTopic() {
        Long trackId = awaitingAudioTrack();
        UUID eventId = UUID.randomUUID();
        doThrow(new TransientDataAccessResourceException("catalog-db is down"))
                .when(trackService).publish(eq(trackId), anyInt());

        send(trackId, event(eventId, trackId, 200));

        ConsumerRecord<String, String> dead = dltRecordWithKey(trackId, TIMEOUT).orElseThrow();
        assertThat(dead.value()).contains(eventId.toString());
        assertThat(header(dead, KafkaHeaders.ORIGINAL_TOPIC)).isEqualTo(TOPIC);
        assertThat(header(dead, KafkaHeaders.EXCEPTION_MESSAGE)).contains("catalog-db is down");
        for (String retryTopic : RETRY_TOPICS) {
            assertThat(records(retryTopic, String.valueOf(trackId), TIMEOUT)).as(retryTopic).hasSize(1);
        }
        // The first delivery and one per retry topic: 4 attempts in all.
        verify(trackService, times(4)).publish(eq(trackId), anyInt());
        assertThat(processedEventRepository.existsById(eventId)).isFalse();
        assertThat(trackRepository.findById(trackId).orElseThrow().getStatus()).isEqualTo(TrackStatus.AWAITING_AUDIO);
        doCallRealMethod().when(trackService).publish(eq(trackId), anyInt());
    }

    @Test
    void eventForAnUnknownTrackGoesStraightToTheDeadLetterTopic() {
        Long unknownTrackId = UNKNOWN_TRACK_IDS.incrementAndGet();

        send(unknownTrackId, event(UUID.randomUUID(), unknownTrackId, 120));

        ConsumerRecord<String, String> dead = dltRecordWithKey(unknownTrackId, TIMEOUT).orElseThrow();
        assertThat(header(dead, KafkaHeaders.EXCEPTION_MESSAGE)).contains("does not exist");
        verify(trackService, times(1)).publish(eq(unknownTrackId), anyInt());
        assertThat(records(RETRY_TOPICS.getFirst(), String.valueOf(unknownTrackId), Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void unreadableEventGoesStraightToTheDeadLetterTopic() {
        Long key = UNKNOWN_TRACK_IDS.incrementAndGet();

        send(key, "{ this is not an AssetReadySnapshot");

        ConsumerRecord<String, String> dead = dltRecordWithKey(key, TIMEOUT).orElseThrow();
        assertThat(header(dead, KafkaHeaders.EXCEPTION_MESSAGE)).contains("Unreadable AssetReadySnapshot");
        assertThat(records(RETRY_TOPICS.getFirst(), String.valueOf(key), Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void invalidEventGoesStraightToTheDeadLetterTopic() {
        Long trackId = awaitingAudioTrack();

        // durationSeconds must be at least 1.
        send(trackId, event(UUID.randomUUID(), trackId, 0));

        ConsumerRecord<String, String> dead = dltRecordWithKey(trackId, TIMEOUT).orElseThrow();
        assertThat(header(dead, KafkaHeaders.EXCEPTION_MESSAGE)).contains("Invalid AssetReadySnapshot");
        assertThat(trackRepository.findById(trackId).orElseThrow().getStatus()).isEqualTo(TrackStatus.AWAITING_AUDIO);
    }

    private Long awaitingAudioTrack() {
        Track track = new Track();
        track.setTitle("Track awaiting audio");
        track.setArtistId(7L);
        track.setStatus(TrackStatus.AWAITING_AUDIO);
        return trackRepository.save(track).getId();
    }

    private void send(Long key, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, String.valueOf(key), payload);
        record.headers().add("eventType", AssetReadyHandler.EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).join();
    }

    private static String event(UUID eventId, Long trackId, int durationSeconds) {
        return """
                {"eventId":"%s","occurredAt":"2026-10-13T10:00:00Z","assetId":%d,"trackId":%d,
                 "durationSeconds":%d,"qualities":["HIGH"],
                 "storageUrls":[{"quality":"HIGH","url":"s3://tracks/%d/master"}]}
                """.formatted(eventId, trackId, trackId, durationSeconds, trackId);
    }

    private Optional<ConsumerRecord<String, String>> dltRecordWithKey(Long key, Duration timeout) {
        return records(DLT, String.valueOf(key), timeout).stream().findFirst();
    }

    /** Reads the topic from the start until a record with the key shows up or the time is over. */
    private List<ConsumerRecord<String, String>> records(String topic, String key, Duration timeout) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "test-" + UUID.randomUUID(), false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plus(timeout);
            while (matching.isEmpty() && Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    if (key.equals(record.key())) {
                        matching.add(record);
                    }
                }
            }
            // Pick up any further copies already in the topic.
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (key.equals(record.key())) {
                    matching.add(record);
                }
            }
        }
        return matching;
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}

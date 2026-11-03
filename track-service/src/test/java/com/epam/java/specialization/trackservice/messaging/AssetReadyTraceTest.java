package com.epam.java.specialization.trackservice.messaging;

import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import com.epam.java.specialization.trackservice.repository.TrackRepository;
import com.epam.java.specialization.trackservice.service.TrackService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * The consumer continues the producer's trace from the {@code traceparent} record header (W3C Trace Context),
 * and a record forwarded to the dead letter topic carries the same trace on.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-kafka-trace;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true",
        "events.topics.partitions=1",
        "events.consumer.retry.delay-ms=100",
        "events.consumer.retry.max-delay-ms=1000",
})
@EmbeddedKafka(partitions = 1)
@AutoConfigureTracing
class AssetReadyTraceTest {

    private static final String TOPIC = "streaming.asset.ready";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private Tracer tracer;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @MockitoSpyBean
    private TrackService trackService;

    @Test
    void trackIsPublishedInTheTraceOfTheEvent() {
        Long trackId = awaitingAudioTrack();
        UUID eventId = UUID.randomUUID();
        AtomicReference<String> publishTraceId = new AtomicReference<>();
        doAnswer(invocation -> {
            publishTraceId.set(tracer.currentSpan().context().traceId());
            return invocation.callRealMethod();
        }).when(trackService).publish(eq(trackId), anyInt());

        String traceId = sendInNewTrace(trackId, event(eventId, trackId, 245));

        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(eventId));
        assertThat(publishTraceId.get()).isEqualTo(traceId);
    }

    @Test
    void deadLetterRecordKeepsTheTraceOfTheEvent() {
        long unknownTrackId = 950_001L;

        String traceId = sendInNewTrace(unknownTrackId, event(UUID.randomUUID(), unknownTrackId, 120));

        ConsumerRecord<String, String> dead = singleRecord(TOPIC + "-dlt", String.valueOf(unknownTrackId));
        String traceparent = new String(dead.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8);
        assertThat(traceparent).startsWith("00-" + traceId + "-");
    }

    /** Sends like streaming-service's relay does: inside a span, so the record carries its traceparent. */
    private String sendInNewTrace(Long key, String payload) {
        Span span = tracer.nextSpan().name("outbox send").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, String.valueOf(key), payload);
            record.headers().add("eventType", AssetReadyHandler.EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record).join();
        } finally {
            span.end();
        }
        return span.context().traceId();
    }

    private Long awaitingAudioTrack() {
        Track track = new Track();
        track.setTitle("Traced track");
        track.setArtistId(7L);
        track.setStatus(TrackStatus.AWAITING_AUDIO);
        return trackRepository.save(track).getId();
    }

    private static String event(UUID eventId, Long trackId, int durationSeconds) {
        return """
                {"eventId":"%s","occurredAt":"2026-10-28T10:00:00Z","assetId":%d,"trackId":%d,
                 "durationSeconds":%d,"qualities":["HIGH"],
                 "storageUrls":[{"quality":"HIGH","url":"s3://tracks/%d/master"}]}
                """.formatted(eventId, trackId, trackId, durationSeconds, trackId);
    }

    private ConsumerRecord<String, String> singleRecord(String topic, String key) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "trace-" + UUID.randomUUID(), false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No record with key " + key + " in " + topic);
    }
}

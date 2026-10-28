package com.epam.java.specialization.notificationservice.kafka;

import com.epam.java.specialization.notificationservice.model.NotificationLog;
import com.epam.java.specialization.notificationservice.repository.NotificationLogRepository;
import com.epam.java.specialization.notificationservice.repository.ProcessedEventRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * The consumer continues auth-service's trace from the {@code traceparent} record header (W3C Trace Context),
 * and a record forwarded to the dead letter topic carries the same trace on.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"auth.user.email", "auth.user.email.DLT"})
@AutoConfigureTracing
class AuthEmailTraceTest {

    private static final String TOPIC = "auth.user.email";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private Tracer tracer;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @MockitoSpyBean
    private NotificationLogRepository notificationLogRepository;

    @Test
    void emailIsRecordedInTheTraceOfTheEvent() {
        String eventId = UUID.randomUUID().toString();
        AtomicReference<String> saveTraceId = new AtomicReference<>();
        doAnswer(invocation -> {
            saveTraceId.set(tracer.currentSpan().context().traceId());
            // A repository interface has no real method to call; the delivery row itself does not matter here.
            return invocation.getArgument(0);
        }).when(notificationLogRepository).save(any(NotificationLog.class));

        String traceId = sendInNewTrace("7", payload(eventId));

        await().atMost(TIMEOUT).until(() -> processedEventRepository.existsById(eventId));
        assertThat(saveTraceId.get()).isEqualTo(traceId);
    }

    @Test
    void deadLetterRecordKeepsTheTraceOfTheEvent() {
        String key = UUID.randomUUID().toString();

        String traceId = sendInNewTrace(key, "not-valid-json");

        ConsumerRecord<String, String> dead = singleRecord(TOPIC + ".DLT", key);
        String traceparent = new String(dead.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8);
        assertThat(traceparent).startsWith("00-" + traceId + "-");
    }

    /** Sends like auth-service's outbox poller does: inside a span, so the record carries its traceparent. */
    private String sendInNewTrace(String key, String payload) {
        Span span = tracer.nextSpan().name("outbox send").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            kafkaTemplate.send(TOPIC, key, payload).join();
        } finally {
            span.end();
        }
        return span.context().traceId();
    }

    private static String payload(String eventId) {
        return """
                {"eventId":"%s","occurredAt":"2026-10-28T10:00:00Z","userId":7,"to":"traced@example.com",
                 "template":"WELCOME","variables":{"displayName":"Traced"}}
                """.formatted(eventId);
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

package com.epam.java.specialization.authservice.outbox;

import com.epam.java.specialization.authservice.api.dto.RegisterRequestDto;
import com.epam.java.specialization.authservice.api.dto.RegistrationRoleDto;
import com.epam.java.specialization.authservice.service.AuthService;
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
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-outbox-trace;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "outbox.poll-interval-ms=200",
})
@EmbeddedKafka(partitions = 1, topics = "auth.user.email")
@AutoConfigureTracing
class OutboxTracePropagationTest {

    private static final String TOPIC = "auth.user.email";

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private Tracer tracer;
    @Autowired
    private AuthService authService;
    @Autowired
    private OutboxEventRepository outboxRepository;

    @Test
    void emailEventContinuesTheTraceOfTheRegistration() {
        Span request = tracer.nextSpan().name("register").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(request)) {
            authService.register(new RegisterRequestDto(
                    "traced@example.com", "password123", "traced", RegistrationRoleDto.LISTENER));
        } finally {
            request.end();
        }
        String traceId = request.context().traceId();

        OutboxEvent event = outboxRepository.findAll().getFirst();
        // version-traceId-parentSpanId-flags: the row's parent is the request span.
        assertThat(event.getTraceparent()).startsWith("00-" + traceId + "-" + request.context().spanId() + "-");
        await().atMost(Duration.ofSeconds(20))
                .until(() -> outboxRepository.findById(event.getId()).orElseThrow().getPublishedAt() != null);

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "trace-test", false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(TOPIC));
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));
            String recordTraceparent = new String(record.headers().lastHeader("traceparent").value(),
                    StandardCharsets.UTF_8);
            // Same trace, but the parent is the poller's producer span, not the request.
            assertThat(recordTraceparent).startsWith("00-" + traceId + "-")
                    .isNotEqualTo(event.getTraceparent());
        }
    }
}

package com.epam.java.specialization.notificationservice.kafka;

import com.epam.java.specialization.notificationservice.repository.NotificationLogRepository;
import com.epam.java.specialization.notificationservice.repository.ProcessedEventRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers",
        topics = {"auth.user.email", "auth.user.email.DLT"}
)
class AuthEmailEventConsumerTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @BeforeEach
    void cleanUp() {
        notificationLogRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    /**
     * Publishes a payload that cannot be deserialized as AuthEmailEvent.
     * The consumer throws on all 3 attempts and the message must arrive in the DLT.
     */
    @Test
    void routesUnprocessableEventToDlt() throws Exception {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-verify-group", "true", embeddedKafkaBroker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> dltConsumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {

            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, "auth.user.email.DLT");

            kafkaTemplate.send("auth.user.email", "not-valid-json").get();

            await().atMost(30, SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records =
                        KafkaTestUtils.getRecords(dltConsumer, Duration.ofMillis(500));
                assertThat(records.isEmpty()).isFalse();
            });
        }
    }

    /**
     * Publishes the same event twice. The second delivery must be skipped so that
     * exactly one NotificationLog row and one ProcessedEvent row exist.
     */
    @Test
    void processesEventExactlyOnce() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String payload = validPayload(eventId);

        kafkaTemplate.send("auth.user.email", "42", payload).get();
        kafkaTemplate.send("auth.user.email", "42", payload).get();

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(eventId)).isTrue()
        );

        assertThat(notificationLogRepository.count()).isOne();
        assertThat(processedEventRepository.count()).isOne();
    }

    private static String validPayload(String eventId) {
        return """
                {
                  "eventId": "%s",
                  "occurredAt": "2026-10-13T10:00:00Z",
                  "userId": 42,
                  "to": "alice@example.com",
                  "template": "WELCOME",
                  "variables": {"displayName": "Alice"}
                }
                """.formatted(eventId);
    }
}

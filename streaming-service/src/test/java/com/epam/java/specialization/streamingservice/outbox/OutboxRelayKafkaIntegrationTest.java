package com.epam.java.specialization.streamingservice.outbox;

import com.epam.java.specialization.streamingservice.model.TranscodingJob;
import com.epam.java.specialization.streamingservice.repository.TranscodingJobRepository;
import com.epam.java.specialization.streamingservice.service.TranscodingService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

/** The relay takes the committed event from the outbox, sends it to Kafka keyed by track id and marks it sent. */
@SpringBootTest(properties = {
        // Own database: the relay of this context must not see outbox rows left by other test contexts.
        "spring.datasource.url=jdbc:h2:mem:streaming-relay;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=true",
        "events.topics.partitions=1",
        "outbox.relay.enabled=true",
        "outbox.relay.poll-interval-ms=200",
})
@EmbeddedKafka(partitions = 1)
class OutboxRelayKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private TranscodingService transcodingService;
    @Autowired
    private TranscodingJobRepository jobRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void committedEventIsPublishedToKafkaAndMarkedSent() {
        TranscodingJob job = new TranscodingJob(42L);
        job.queue("master.mp3", 200_000);
        job.startProcessing();
        Long jobId = jobRepository.saveAndFlush(job).getId();

        transcodingService.complete(jobId, 245, "s3://tracks/42/master");

        OutboxEvent event = outboxRepository.findAll().getFirst();
        await().atMost(Duration.ofSeconds(20))
                .until(() -> outboxRepository.findById(event.getId()).orElseThrow().getSentAt() != null);

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "relay-test", false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of("streaming.asset.ready"));
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, "streaming.asset.ready", Duration.ofSeconds(10));
            assertThat(record.key()).isEqualTo("42");
            assertThat(record.value()).isEqualTo(event.getPayload());
            assertThat(new String(record.headers().lastHeader(OutboxRelay.EVENT_TYPE_HEADER).value(),
                    StandardCharsets.UTF_8)).isEqualTo(TranscodingService.ASSET_READY_EVENT);
        }
    }
}

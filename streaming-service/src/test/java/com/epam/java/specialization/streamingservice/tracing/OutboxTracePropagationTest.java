package com.epam.java.specialization.streamingservice.tracing;

import com.epam.java.specialization.streamingservice.model.TranscodingJob;
import com.epam.java.specialization.streamingservice.outbox.OutboxEvent;
import com.epam.java.specialization.streamingservice.outbox.OutboxEventRepository;
import com.epam.java.specialization.streamingservice.repository.TranscodingJobRepository;
import com.epam.java.specialization.streamingservice.service.StreamingStorageService;
import com.epam.java.specialization.streamingservice.service.TranscodingService;
import com.epam.java.specialization.streamingservice.service.TranscodingWorker;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * The trace of an upload survives the two scheduled steps after it: the worker continues it from the job,
 * the outbox row stores it, and the relay sends the Kafka record with a {@code traceparent} of the same trace.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streaming-trace;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=true",
        "events.topics.partitions=1",
        "outbox.relay.enabled=true",
        "outbox.relay.poll-interval-ms=200",
        // The test runs the worker itself.
        "transcoding.worker.enabled=true",
        "transcoding.worker.poll-interval-ms=3600000",
})
@EmbeddedKafka(partitions = 1)
@AutoConfigureTracing
class OutboxTracePropagationTest {

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private Tracer tracer;
    @Autowired
    private TranscodingService transcodingService;
    @Autowired
    private TranscodingWorker worker;
    @Autowired
    private TranscodingJobRepository jobRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @MockitoBean
    private StreamingStorageService storageService;

    @BeforeEach
    void cleanUp() throws Exception {
        outboxRepository.deleteAll();
        jobRepository.deleteAll();
        given(storageService.objectSize(anyString())).willReturn(200_000L);
        given(storageService.storageUrl(anyString())).willReturn("s3://tracks/77/master");
    }

    @Test
    void kafkaRecordContinuesTheTraceOfTheUpload() {
        Span upload = tracer.nextSpan().name("upload").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(upload)) {
            transcodingService.queue(77L, "master.mp3", 200_000);
        } finally {
            upload.end();
        }
        String traceId = upload.context().traceId();

        TranscodingJob job = jobRepository.findByTrackId(77L).orElseThrow();
        // version-traceId-parentSpanId-flags: the job's parent is the upload span.
        assertThat(job.getTraceparent()).startsWith("00-" + traceId + "-" + upload.context().spanId() + "-");

        worker.processNext();

        OutboxEvent event = outboxRepository.findAll().getFirst();
        assertThat(event.getTraceparent()).startsWith("00-" + traceId + "-")
                .isNotEqualTo(job.getTraceparent());
        await().atMost(Duration.ofSeconds(20))
                .until(() -> outboxRepository.findById(event.getId()).orElseThrow().getSentAt() != null);

        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "trace-test", false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of("streaming.asset.ready"));
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, "streaming.asset.ready", Duration.ofSeconds(10));
            String recordTraceparent = new String(record.headers().lastHeader("traceparent").value(),
                    StandardCharsets.UTF_8);
            // Same trace, but the parent is the relay's producer span, not the transcoding step.
            assertThat(recordTraceparent).startsWith("00-" + traceId + "-")
                    .isNotEqualTo(event.getTraceparent());
        }
    }
}

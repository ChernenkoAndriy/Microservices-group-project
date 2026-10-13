package com.epam.java.specialization.streamingservice.outbox;

import com.epam.java.specialization.streamingservice.model.TranscodingJob;
import com.epam.java.specialization.streamingservice.model.TranscodingJobStatus;
import com.epam.java.specialization.streamingservice.repository.TranscodingJobRepository;
import com.epam.java.specialization.streamingservice.service.TranscodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** While Kafka is unreachable the job still becomes READY, and its event waits in the outbox instead of being lost. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streaming-kafka-down;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.producer.properties.max.block.ms=500",
        "spring.kafka.producer.properties.request.timeout.ms=500",
        "spring.kafka.producer.properties.delivery.timeout.ms=1000",
        "outbox.relay.send-timeout-ms=2000",
        "outbox.relay.enabled=true",
        // The test runs the relay itself.
        "outbox.relay.poll-interval-ms=3600000",
})
class OutboxRelayKafkaDownTest {

    @Autowired
    private TranscodingService transcodingService;
    @Autowired
    private TranscodingJobRepository jobRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private OutboxRelay relay;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void eventStaysInTheOutboxUntilKafkaIsBack() {
        TranscodingJob job = new TranscodingJob(77L);
        job.queue("master.mp3", 200_000);
        job.startProcessing();
        Long jobId = jobRepository.saveAndFlush(job).getId();
        transcodingService.complete(jobId, 245, "s3://tracks/77/master");

        relay.relay();

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(TranscodingJobStatus.READY);
        OutboxEvent event = outboxRepository.findAll().getFirst();
        assertThat(event.getSentAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isNotBlank();
    }
}

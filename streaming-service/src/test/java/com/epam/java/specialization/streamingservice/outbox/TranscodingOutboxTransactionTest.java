package com.epam.java.specialization.streamingservice.outbox;

import com.epam.java.specialization.streamingservice.model.TranscodingJob;
import com.epam.java.specialization.streamingservice.model.TranscodingJobStatus;
import com.epam.java.specialization.streamingservice.repository.TranscodingJobRepository;
import com.epam.java.specialization.streamingservice.service.TranscodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The job and its outbox event are written by one transaction manager in one database transaction:
 * either both are committed or neither is, so there is no dual write.
 */
@SpringBootTest
class TranscodingOutboxTransactionTest {

    private static final AtomicLong TRACK_IDS = new AtomicLong(1000);

    @Autowired
    private TranscodingService transcodingService;
    @Autowired
    private TranscodingJobRepository jobRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private OutboxWriter outboxWriter;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void completingAJobCommitsTheJobAndItsEventTogether() {
        TranscodingJob job = processingJob();

        transcodingService.complete(job.getId(), 245, "s3://tracks/" + job.getTrackId() + "/master");

        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(TranscodingJobStatus.READY);
        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        OutboxEvent event = events.getFirst();
        assertThat(event.getTopic()).isEqualTo("streaming.asset.ready");
        assertThat(event.getMessageKey()).isEqualTo(String.valueOf(job.getTrackId()));
        assertThat(event.getEventType()).isEqualTo(TranscodingService.ASSET_READY_EVENT);
        assertThat(event.getPayload())
                .contains("\"eventId\":\"" + event.getId() + "\"")
                .contains("\"trackId\":" + job.getTrackId())
                .contains("\"durationSeconds\":245");
        assertThat(event.getSentAt()).isNull();
    }

    @Test
    void failureAfterBothWritesRollsBackTheJobAndTheEvent() {
        TranscodingJob job = processingJob();

        // complete() joins the surrounding transaction; it fails after the job and the event are both flushed.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            transcodingService.complete(job.getId(), 245, "s3://tracks/x/master");
            outboxRepository.flush();
            assertThat(outboxRepository.count()).isEqualTo(1);
            throw new IllegalStateException("crash before the commit");
        })).hasMessage("crash before the commit");

        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(TranscodingJobStatus.PROCESSING);
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void outboxCannotBeWrittenOutsideATransaction() {
        assertThatThrownBy(() -> outboxWriter.append(UUID.randomUUID(), "TranscodingJob", 1L,
                "streaming.asset.ready", TranscodingService.ASSET_READY_EVENT, "{}"))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(outboxRepository.count()).isZero();
    }

    private TranscodingJob processingJob() {
        TranscodingJob job = new TranscodingJob(TRACK_IDS.incrementAndGet());
        job.queue("master.mp3", 200_000);
        job.startProcessing();
        return jobRepository.saveAndFlush(job);
    }
}

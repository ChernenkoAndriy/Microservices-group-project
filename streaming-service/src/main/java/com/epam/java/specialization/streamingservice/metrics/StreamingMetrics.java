package com.epam.java.specialization.streamingservice.metrics;

import com.epam.java.specialization.streamingservice.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business metrics of the streaming service, exported at {@code /actuator/prometheus}.
 * <ul>
 *   <li>{@code spotty_outbox_pending_events} (Gauge): outbox events not yet sent to Kafka; grows while Kafka is down.</li>
 *   <li>{@code spotty_transcoding_active_seconds} (LongTaskTimer): transcodings in progress and how long they run.</li>
 *   <li>{@code spotty_upload_master_size_bytes} (DistributionSummary): sizes of uploaded master files.</li>
 * </ul>
 */
@Component
public class StreamingMetrics {

    public static final String OUTBOX_PENDING = "spotty.outbox.pending.events";
    public static final String TRANSCODING_ACTIVE = "spotty.transcoding.active";
    public static final String UPLOAD_MASTER_SIZE = "spotty.upload.master.size";

    private final LongTaskTimer transcodingActive;
    private final DistributionSummary uploadMasterSize;

    public StreamingMetrics(MeterRegistry registry, OutboxEventRepository outboxRepository) {
        Gauge.builder(OUTBOX_PENDING, outboxRepository, OutboxEventRepository::countBySentAtIsNull)
                .description("Outbox events waiting to be sent to Kafka")
                .register(registry);
        this.transcodingActive = LongTaskTimer.builder(TRANSCODING_ACTIVE)
                .description("Transcoding jobs currently in progress")
                .register(registry);
        this.uploadMasterSize = DistributionSummary.builder(UPLOAD_MASTER_SIZE)
                .description("Size of uploaded master audio files")
                .baseUnit("bytes")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void recordTranscoding(Runnable transcoding) {
        transcodingActive.record(transcoding);
    }

    public void recordUploadedMaster(long sizeBytes) {
        uploadMasterSize.record(sizeBytes);
    }
}

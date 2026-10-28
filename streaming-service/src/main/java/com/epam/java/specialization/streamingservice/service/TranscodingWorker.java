package com.epam.java.specialization.streamingservice.service;

import com.epam.java.specialization.streamingservice.metrics.StreamingMetrics;
import com.epam.java.specialization.streamingservice.model.TranscodingJob;
import com.epam.java.specialization.streamingservice.tracing.TraceContextCarrier;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Processes queued transcoding jobs one by one. There is no real transcoder yet: the master is served
 * as the HIGH quality, and the duration is estimated from its size at 128 kbit/s. Each job is processed
 * in a span that continues the trace of its upload.
 */
@Component
@ConditionalOnProperty(name = "transcoding.worker.enabled", havingValue = "true", matchIfMissing = true)
public class TranscodingWorker {

    static final int ASSUMED_BITRATE_BITS_PER_SECOND = 128_000;
    static final int MAX_DURATION_SECONDS = 21_600;

    private static final Logger log = LoggerFactory.getLogger(TranscodingWorker.class);

    private final TranscodingService transcodingService;
    private final StreamingStorageService storageService;
    private final StreamingMetrics metrics;
    private final long simulatedDelayMs;
    private final TraceContextCarrier traceContext;

    public TranscodingWorker(TranscodingService transcodingService, StreamingStorageService storageService,
                             StreamingMetrics metrics,
                             @Value("${transcoding.worker.simulated-delay-ms:0}") long simulatedDelayMs,
                             TraceContextCarrier traceContext) {
        this.transcodingService = transcodingService;
        this.storageService = storageService;
        this.metrics = metrics;
        this.simulatedDelayMs = simulatedDelayMs;
        this.traceContext = traceContext;
    }

    @Scheduled(fixedDelayString = "${transcoding.worker.poll-interval-ms:2000}")
    public void processNext() {
        TranscodingJob job = transcodingService.claimNext().orElse(null);
        if (job == null) {
            return;
        }
        Span span = traceContext.startSpan("transcode", job.getTraceparent())
                .tag("track.id", String.valueOf(job.getTrackId()))
                .tag("transcoding.job.id", String.valueOf(job.getId()));
        try (Tracer.SpanInScope scope = traceContext.tracer().withSpan(span)) {
            metrics.recordTranscoding(() -> transcode(job, span));
        } finally {
            span.end();
        }
    }

    private void transcode(TranscodingJob job, Span span) {
        String key = StreamingStorageService.masterKey(job.getTrackId());
        try {
            simulateWork();
            long sizeBytes = storageService.objectSize(key);
            transcodingService.complete(job.getId(), estimateDurationSeconds(sizeBytes), storageService.storageUrl(key));
        } catch (Exception e) {
            log.warn("Could not transcode track id={}", job.getTrackId(), e);
            span.error(e);
            transcodingService.fail(job.getId(), e.getMessage());
        }
    }

    /** Stands in for real transcoding time, so the active-transcoding metric can be observed. */
    private void simulateWork() throws InterruptedException {
        if (simulatedDelayMs > 0) {
            Thread.sleep(simulatedDelayMs);
        }
    }

    static int estimateDurationSeconds(long sizeBytes) {
        long seconds = sizeBytes * 8 / ASSUMED_BITRATE_BITS_PER_SECOND;
        return (int) Math.max(1, Math.min(MAX_DURATION_SECONDS, seconds));
    }
}

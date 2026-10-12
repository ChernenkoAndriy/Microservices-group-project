package com.epam.java.specialization.streamingservice.service;

import com.epam.java.specialization.streamingservice.model.TranscodingJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Processes queued transcoding jobs one by one. There is no real transcoder yet: the master is served
 * as the HIGH quality, and the duration is estimated from its size at 128 kbit/s.
 */
@Component
@ConditionalOnProperty(name = "transcoding.worker.enabled", havingValue = "true", matchIfMissing = true)
public class TranscodingWorker {

    static final int ASSUMED_BITRATE_BITS_PER_SECOND = 128_000;
    static final int MAX_DURATION_SECONDS = 21_600;

    private static final Logger log = LoggerFactory.getLogger(TranscodingWorker.class);

    private final TranscodingService transcodingService;
    private final StreamingStorageService storageService;

    public TranscodingWorker(TranscodingService transcodingService, StreamingStorageService storageService) {
        this.transcodingService = transcodingService;
        this.storageService = storageService;
    }

    @Scheduled(fixedDelayString = "${transcoding.worker.poll-interval-ms:2000}")
    public void processNext() {
        TranscodingJob job = transcodingService.claimNext().orElse(null);
        if (job == null) {
            return;
        }
        String key = StreamingStorageService.masterKey(job.getTrackId());
        try {
            long sizeBytes = storageService.objectSize(key);
            transcodingService.complete(job.getId(), estimateDurationSeconds(sizeBytes), storageService.storageUrl(key));
        } catch (Exception e) {
            log.warn("Could not transcode track id={}", job.getTrackId(), e);
            transcodingService.fail(job.getId(), e.getMessage());
        }
    }

    static int estimateDurationSeconds(long sizeBytes) {
        long seconds = sizeBytes * 8 / ASSUMED_BITRATE_BITS_PER_SECOND;
        return (int) Math.max(1, Math.min(MAX_DURATION_SECONDS, seconds));
    }
}

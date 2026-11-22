package com.epam.java.specialization.streamingservice.service;

import com.epam.java.specialization.streamingservice.event.AssetReadySnapshot;
import com.epam.java.specialization.streamingservice.event.AudioQuality;
import com.epam.java.specialization.streamingservice.event.StorageUrl;
import com.epam.java.specialization.streamingservice.model.TranscodingJob;
import com.epam.java.specialization.streamingservice.outbox.OutboxWriter;
import com.epam.java.specialization.streamingservice.repository.TranscodingJobRepository;
import com.epam.java.specialization.streamingservice.tracing.TraceContextCarrier;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class TranscodingService {

    public static final String ASSET_READY_EVENT = "AssetReadySnapshot";

    private static final Logger log = LoggerFactory.getLogger(TranscodingService.class);

    private final TranscodingJobRepository jobRepository;
    private final OutboxWriter outboxWriter;
    private final String assetReadyTopic;
    private final TraceContextCarrier traceContext;
    private final StreamingStorageService storageService;

    public TranscodingService(TranscodingJobRepository jobRepository, OutboxWriter outboxWriter,
                              @Value("${events.topics.asset-ready}") String assetReadyTopic,
                              TraceContextCarrier traceContext, StreamingStorageService storageService) {
        this.jobRepository = jobRepository;
        this.outboxWriter = outboxWriter;
        this.assetReadyTopic = assetReadyTopic;
        this.traceContext = traceContext;
        this.storageService = storageService;
    }

    /**
     * After a new cover is stored: tells the catalog again (same event, now with {@code coverUrl}) when the audio is
     * already READY. Otherwise the cover goes out with the event of the transcoding that is still to finish.
     */
    @Transactional
    public void coverChanged(Long trackId) {
        jobRepository.findByTrackId(trackId)
                .filter(job -> job.getStatus() == com.epam.java.specialization.streamingservice.model.TranscodingJobStatus.READY)
                .ifPresent(job -> emitAssetReady(job, job.getDurationSeconds(),
                        storageService.storageUrl(StreamingStorageService.masterKey(trackId))));
    }

    /** Queues the transcoding of a master that is already in storage. */
    @Transactional
    public TranscodingJob queue(Long trackId, String masterFileName, long masterSizeBytes) {
        TranscodingJob job = jobRepository.findByTrackId(trackId).orElseGet(() -> new TranscodingJob(trackId));
        job.queue(masterFileName, masterSizeBytes);
        job.rememberTrace(traceContext.currentTraceparent());
        TranscodingJob saved = jobRepository.saveAndFlush(job);
        log.info("Queued transcoding job id={} for track id={}", saved.getId(), trackId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<TranscodingJob> find(Long trackId) {
        return jobRepository.findByTrackId(trackId);
    }

    /** Takes the oldest queued job, if any, and marks it as processing. */
    @Transactional
    public Optional<TranscodingJob> claimNext() {
        return jobRepository.lockNextQueued().map(job -> {
            job.startProcessing();
            return job;
        });
    }

    /**
     * Marks the job ready and puts {@code AssetReadySnapshot} in the outbox. Both writes share one transaction,
     * so the job is never READY without its event, and there is never an event for a job that is not READY.
     */
    @Observed(name = "spotty.transcoding.complete", contextualName = "complete-transcoding")
    @Transactional
    public void complete(Long jobId, int durationSeconds, String storageUrl) {
        TranscodingJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Transcoding job " + jobId + " does not exist"));
        Set<String> qualities = new LinkedHashSet<>(List.of(AudioQuality.HIGH.getValue()));
        job.markReady(durationSeconds, qualities);
        emitAssetReady(job, durationSeconds, storageUrl);
    }

    private void emitAssetReady(TranscodingJob job, int durationSeconds, String storageUrl) {
        Long jobId = job.getId();
        UUID eventId = UUID.randomUUID();
        AssetReadySnapshot event = new AssetReadySnapshot(
                eventId,
                OffsetDateTime.now(ZoneOffset.UTC),
                job.getId(),
                job.getTrackId(),
                durationSeconds,
                new LinkedHashSet<>(List.of(AudioQuality.HIGH)),
                List.of(new StorageUrl(AudioQuality.HIGH, storageUrl)));
        event.setCoverUrl(storageService.coverUrlOrNull(job.getTrackId()));
        outboxWriter.append(eventId, "TranscodingJob", job.getTrackId(), assetReadyTopic, ASSET_READY_EVENT, event);
        log.info("Transcoding job id={} for track id={} is ready, event id={} is in the outbox",
                jobId, job.getTrackId(), eventId);
    }

    @Transactional
    public void fail(Long jobId, String reason) {
        jobRepository.findById(jobId).ifPresent(job -> job.markFailed(reason));
        log.warn("Transcoding job id={} failed: {}", jobId, reason);
    }
}

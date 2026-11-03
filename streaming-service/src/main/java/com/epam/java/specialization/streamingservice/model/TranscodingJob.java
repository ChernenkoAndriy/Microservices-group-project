package com.epam.java.specialization.streamingservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The audio of one track: the uploaded master and its transcoding. The id is the {@code assetId}
 * of the {@code AssetReadySnapshot} event.
 */
@Entity
@Table(name = "transcoding_job")
public class TranscodingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long trackId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TranscodingJobStatus status = TranscodingJobStatus.QUEUED;

    @Column(nullable = false)
    private int progressPercent;

    /** Comma-separated {@code AudioQuality} names that can be streamed. */
    @Column(length = 50)
    private String qualities;

    private Integer durationSeconds;

    @Column(length = 255)
    private String masterFileName;

    private Long masterSizeBytes;

    @Column(length = 500)
    private String failureReason;

    /** W3C {@code traceparent} of the upload, so the worker continues the upload's trace. */
    @Column(length = 64)
    private String traceparent;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected TranscodingJob() {
    }

    public TranscodingJob(Long trackId) {
        this.trackId = trackId;
    }

    /** Starts over with a new master: a re-upload replaces the previous audio. */
    public void queue(String masterFileName, long masterSizeBytes) {
        this.status = TranscodingJobStatus.QUEUED;
        this.progressPercent = 0;
        this.qualities = null;
        this.durationSeconds = null;
        this.failureReason = null;
        this.masterFileName = masterFileName;
        this.masterSizeBytes = masterSizeBytes;
    }

    public void rememberTrace(String traceparent) {
        this.traceparent = traceparent;
    }

    public void startProcessing() {
        this.status = TranscodingJobStatus.PROCESSING;
        this.progressPercent = 10;
    }

    public void markReady(int durationSeconds, Set<String> qualities) {
        this.status = TranscodingJobStatus.READY;
        this.progressPercent = 100;
        this.durationSeconds = durationSeconds;
        this.qualities = String.join(",", qualities);
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = TranscodingJobStatus.FAILED;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
    }

    public Set<String> getQualities() {
        if (qualities == null || qualities.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(qualities.split(",")).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Long getId() { return id; }
    public Long getTrackId() { return trackId; }
    public TranscodingJobStatus getStatus() { return status; }
    public int getProgressPercent() { return progressPercent; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public String getMasterFileName() { return masterFileName; }
    public Long getMasterSizeBytes() { return masterSizeBytes; }
    public String getFailureReason() { return failureReason; }
    public String getTraceparent() { return traceparent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

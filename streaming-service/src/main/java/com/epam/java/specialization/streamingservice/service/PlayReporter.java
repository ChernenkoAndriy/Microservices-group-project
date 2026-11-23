package com.epam.java.specialization.streamingservice.service;

import com.epam.java.specialization.grpc.catalog.v1.PlayEvent;
import com.epam.java.specialization.grpc.catalog.v1.PlaySummary;
import com.epam.java.specialization.streamingservice.client.CatalogGrpcClient;
import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Counts plays in catalog-service. A served stream adds a play event to an in-memory buffer; every few seconds
 * the buffer is sent as one client-streaming ReportPlays call, instead of one call per play.
 * <p>
 * Play counts are statistics, so delivery is at most once: a batch that fails is logged and dropped, never sent
 * again, because catalog-service may already have counted it and the call is not idempotent. The buffer is in
 * memory, so plays not yet sent are lost on a restart.
 */
@Component
public class PlayReporter {

    private static final Logger log = LoggerFactory.getLogger(PlayReporter.class);
    private static final int MAX_BATCH = 500;

    private final CatalogGrpcClient catalog;
    private final Clock clock = Clock.systemUTC();
    private final ConcurrentLinkedQueue<PlayEvent> pending = new ConcurrentLinkedQueue<>();

    public PlayReporter(CatalogGrpcClient catalog) {
        this.catalog = catalog;
    }

    /**
     * @param userId the listener, or {@code null} when the stream request does not name one
     * @param listenedSeconds how much of the track was played; 0 while the player does not report progress
     */
    public void recordPlay(long trackId, Long userId, int listenedSeconds) {
        Instant now = clock.instant();
        pending.add(PlayEvent.newBuilder()
                .setTrackId(trackId)
                .setUserId(userId != null ? userId : 0)
                .setListenedSeconds(listenedSeconds)
                .setPlayedAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()))
                .build());
    }

    @Scheduled(fixedDelayString = "${catalog-grpc.play-report.interval-ms:5000}")
    public void flush() {
        List<PlayEvent> batch = new ArrayList<>();
        PlayEvent event;
        while (batch.size() < MAX_BATCH && (event = pending.poll()) != null) {
            batch.add(event);
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            PlaySummary summary = catalog.reportPlays(batch);
            log.info("Reported {} plays of {} tracks to catalog-service", summary.getEventsReceived(),
                    summary.getTracksCount());
        } catch (StatusRuntimeException e) {
            log.warn("Dropped {} play events: catalog-service did not accept them ({})", batch.size(), e.getStatus());
        }
    }
}

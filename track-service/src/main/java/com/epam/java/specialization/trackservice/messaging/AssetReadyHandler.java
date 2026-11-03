package com.epam.java.specialization.trackservice.messaging;

import com.epam.java.specialization.trackservice.event.AssetReadySnapshot;
import com.epam.java.specialization.trackservice.exception.CatalogException;
import com.epam.java.specialization.trackservice.service.TrackService;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Publishes the track whose audio is ready (Idempotent Consumer). The event id is stored in the same transaction
 * as the publish, so an event delivered again, after a crash or a redelivery, finds its id and changes nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetReadyHandler {

    public static final String EVENT_TYPE = "AssetReadySnapshot";

    private final ProcessedEventRepository processedEventRepository;
    private final TrackService trackService;
    private final Clock clock;

    /** @return {@code false} when the event had already been applied and was skipped */
    @Observed(name = "spotty.asset-ready.apply", contextualName = "apply-asset-ready")
    @Transactional
    public boolean handle(AssetReadySnapshot event, String topic) {
        if (processedEventRepository.existsById(event.getEventId())) {
            log.info("Skipping duplicate event id={} for track id={}", event.getEventId(), event.getTrackId());
            return false;
        }
        try {
            trackService.publish(event.getTrackId(), event.getDurationSeconds());
        } catch (CatalogException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                throw new NonRetryableEventException(e.getMessage(), e);
            }
            throw e;
        }
        // A concurrent copy of the same event fails here on the primary key and is retried, then skipped.
        processedEventRepository.saveAndFlush(
                new ProcessedEvent(event.getEventId(), EVENT_TYPE, topic, clock.instant()));
        log.info("Applied event id={}: track id={} is published", event.getEventId(), event.getTrackId());
        return true;
    }
}

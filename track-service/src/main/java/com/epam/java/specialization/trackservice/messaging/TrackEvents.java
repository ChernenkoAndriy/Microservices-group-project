package com.epam.java.specialization.trackservice.messaging;

import com.epam.java.specialization.trackservice.event.TrackPublishedEvent;
import com.epam.java.specialization.trackservice.event.TrackSnapshot;
import com.epam.java.specialization.trackservice.mapper.DateTimeMapping;
import com.epam.java.specialization.trackservice.model.Genre;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import com.epam.java.specialization.trackservice.outbox.OutboxWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes the track events of api-contracts/catalog-service/asyncapi.yaml to the outbox, inside the caller's
 * transaction: {@code TrackSnapshot} after every change of a track, {@code TrackPublishedEvent} once, when a track
 * first becomes {@code PUBLISHED}. Both are keyed by the track id.
 */
@Component
public class TrackEvents {

    public static final String AGGREGATE_TYPE = "Track";
    public static final String TRACK_SNAPSHOT = "TrackSnapshot";
    public static final String TRACK_PUBLISHED = "TrackPublishedEvent";

    private final OutboxWriter outbox;
    private final Clock clock;
    private final String snapshotTopic;
    private final String publishedTopic;

    public TrackEvents(OutboxWriter outbox, Clock clock,
                       @Value("${events.topics.track-snapshot}") String snapshotTopic,
                       @Value("${events.topics.track-published}") String publishedTopic) {
        this.outbox = outbox;
        this.clock = clock;
        this.snapshotTopic = snapshotTopic;
        this.publishedTopic = publishedTopic;
    }

    /** The current state of the track. */
    public void snapshot(Track track) {
        snapshot(track, track.getStatus());
    }

    /**
     * A snapshot with the given status. A deleted track is sent as {@code UNPUBLISHED}: the contract has no
     * tombstone, and consumers drop every track that is not {@code PUBLISHED}.
     */
    public void snapshot(Track track, TrackStatus status) {
        UUID eventId = UUID.randomUUID();
        TrackSnapshot event = new TrackSnapshot(eventId, DateTimeMapping.toOffsetDateTime(clock.instant()),
                track.getId(), track.getTitle(), track.getArtistId(), genreSlugs(track),
                com.epam.java.specialization.trackservice.event.TrackStatus.fromValue(status.name()));
        event.setReleaseDate(track.getReleaseDate());
        outbox.append(eventId, AGGREGATE_TYPE, track.getId(), snapshotTopic, TRACK_SNAPSHOT, event);
    }

    public void published(Track track) {
        UUID eventId = UUID.randomUUID();
        TrackPublishedEvent event = new TrackPublishedEvent(eventId,
                DateTimeMapping.toOffsetDateTime(clock.instant()), track.getId(), track.getArtistId(),
                track.getTitle(), genreSlugs(track));
        event.setReleaseDate(track.getReleaseDate());
        outbox.append(eventId, AGGREGATE_TYPE, track.getId(), publishedTopic, TRACK_PUBLISHED, event);
    }

    private static Set<String> genreSlugs(Track track) {
        return track.getGenres().stream().map(Genre::getSlug).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

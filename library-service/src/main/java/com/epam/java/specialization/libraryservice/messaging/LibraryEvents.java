package com.epam.java.specialization.libraryservice.messaging;

import com.epam.java.specialization.libraryservice.event.ArtistFollowChange;
import com.epam.java.specialization.libraryservice.event.TrackLikeChange;
import com.epam.java.specialization.libraryservice.outbox.OutboxWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Writes the events of api-contracts/library-service/asyncapi.yaml to the outbox, inside the caller's transaction.
 * Likes are keyed by user id (one user's like and unlike stay in order), follows by artist id.
 */
@Component
public class LibraryEvents {

    public static final String TRACK_LIKED = "TrackLikedEvent";
    public static final String TRACK_UNLIKED = "TrackUnlikedEvent";
    public static final String ARTIST_FOLLOWED = "ArtistFollowedEvent";
    public static final String ARTIST_UNFOLLOWED = "ArtistUnfollowedEvent";

    private final OutboxWriter outbox;
    private final String likesTopic;
    private final String followsTopic;

    public LibraryEvents(OutboxWriter outbox,
                         @Value("${events.topics.track-likes}") String likesTopic,
                         @Value("${events.topics.artist-follows}") String followsTopic) {
        this.outbox = outbox;
        this.likesTopic = likesTopic;
        this.followsTopic = followsTopic;
    }

    public void trackLiked(Long userId, Long trackId, Instant at) {
        likeChange(TRACK_LIKED, userId, trackId, at);
    }

    public void trackUnliked(Long userId, Long trackId, Instant at) {
        likeChange(TRACK_UNLIKED, userId, trackId, at);
    }

    public void artistFollowed(Long userId, Long artistId, Instant at) {
        followChange(ARTIST_FOLLOWED, userId, artistId, at);
    }

    public void artistUnfollowed(Long userId, Long artistId, Instant at) {
        followChange(ARTIST_UNFOLLOWED, userId, artistId, at);
    }

    private void likeChange(String eventType, Long userId, Long trackId, Instant at) {
        UUID eventId = UUID.randomUUID();
        TrackLikeChange payload = new TrackLikeChange(eventId, at.atOffset(ZoneOffset.UTC), userId, trackId);
        outbox.append(eventId, "User", userId, likesTopic, eventType, payload);
    }

    private void followChange(String eventType, Long userId, Long artistId, Instant at) {
        UUID eventId = UUID.randomUUID();
        ArtistFollowChange payload = new ArtistFollowChange(eventId, at.atOffset(ZoneOffset.UTC), userId, artistId);
        outbox.append(eventId, "Artist", artistId, followsTopic, eventType, payload);
    }
}

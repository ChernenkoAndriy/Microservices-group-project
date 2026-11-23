package com.epam.java.specialization.notificationservice.kafka;

import com.epam.java.specialization.notificationservice.model.ArtistFollower;
import com.epam.java.specialization.notificationservice.model.ProcessedEvent;
import com.epam.java.specialization.notificationservice.model.UserCopy;
import com.epam.java.specialization.notificationservice.repository.ArtistFollowerRepository;
import com.epam.java.specialization.notificationservice.repository.NotificationPreferenceRepository;
import com.epam.java.specialization.notificationservice.repository.ProcessedEventRepository;
import com.epam.java.specialization.notificationservice.repository.UserCopyRepository;
import com.epam.java.specialization.notificationservice.service.ArtistDirectory;
import com.epam.java.specialization.notificationservice.service.DeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Scenario 1: keeps the follower lists and user copies from events, and emails the followers of an artist when the
 * artist publishes a track. Follow and snapshot handlers are upserts, so a repeated event changes nothing.
 */
@Component
public class ReleaseEventConsumers {

    private static final Logger log = LoggerFactory.getLogger(ReleaseEventConsumers.class);

    private final JsonMapper jsonMapper;
    private final ArtistFollowerRepository followers;
    private final UserCopyRepository users;
    private final NotificationPreferenceRepository preferences;
    private final ProcessedEventRepository processedEvents;
    private final DeliveryService delivery;
    private final ArtistDirectory artists;
    private final Clock clock;

    public ReleaseEventConsumers(JsonMapper jsonMapper, ArtistFollowerRepository followers, UserCopyRepository users,
                                 NotificationPreferenceRepository preferences, ProcessedEventRepository processedEvents,
                                 DeliveryService delivery, ArtistDirectory artists, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.followers = followers;
        this.users = users;
        this.preferences = preferences;
        this.processedEvents = processedEvents;
        this.delivery = delivery;
        this.artists = artists;
        this.clock = clock;
    }

    @KafkaListener(topics = "library.artist-follows", groupId = "notification-service")
    @Transactional
    public void onFollowChange(String payload,
                               @Header(name = "eventType", required = false) byte[] eventType,
                               @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        JsonNode event = jsonMapper.readTree(payload);
        Long artistId = event.get("artistId").asLong();
        Long userId = event.get("userId").asLong();
        String type = eventType == null ? "" : new String(eventType, java.nio.charset.StandardCharsets.UTF_8);
        String id = ArtistFollower.key(artistId, userId);
        if (type.contains("Unfollowed")) {
            followers.deleteById(id);
        } else if (!followers.existsById(id)) {
            followers.save(new ArtistFollower(artistId, userId));
        }
        log.debug("{} user={} artist={}", type, userId, artistId);
    }

    @KafkaListener(topics = "auth.user.snapshot", groupId = "notification-service")
    @Transactional
    public void onUserSnapshot(String payload) {
        JsonNode event = jsonMapper.readTree(payload);
        Long userId = event.get("userId").asLong();
        Instant occurredAt = Instant.parse(event.get("occurredAt").asString());
        String email = event.get("email").asString();
        String role = event.get("role").asString();
        String status = event.get("status").asString();
        users.findById(userId).ifPresentOrElse(copy -> {
            if (!occurredAt.isBefore(copy.getOccurredAt())) {
                copy.update(email, role, status, occurredAt);
            }
        }, () -> users.save(new UserCopy(userId, email, role, status, occurredAt)));
    }

    @KafkaListener(topics = "catalog.track.published", groupId = "notification-service")
    @Transactional
    public void onTrackPublished(String payload) {
        JsonNode event = jsonMapper.readTree(payload);
        String eventId = event.get("eventId").asString();
        if (processedEvents.existsById(eventId)) {
            log.debug("Skipping duplicate TrackPublishedEvent id={}", eventId);
            return;
        }
        Long artistId = event.get("artistId").asLong();
        String title = event.get("title").asString();
        var recipients = followers.findByArtistId(artistId).stream()
                .map(f -> users.findById(f.getUserId()).orElse(null))
                .filter(u -> u != null && u.isActive())
                .filter(u -> preferences.findById(u.getUserId()).map(p -> p.isNewReleases()).orElse(true))
                .toList();
        if (!recipients.isEmpty()) {
            Map<String, String> variables = Map.of("artistName", artists.nameOf(artistId), "trackTitle", title);
            recipients.forEach(u -> delivery.deliver(eventId, u.getEmail(), "NEW_RELEASE", variables));
        }
        processedEvents.save(new ProcessedEvent(eventId, clock.instant()));
        log.info("TrackPublishedEvent id={} artist={}: emailed {} followers", eventId, artistId, recipients.size());
    }
}

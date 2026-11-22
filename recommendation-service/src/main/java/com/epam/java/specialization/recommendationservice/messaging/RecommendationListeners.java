package com.epam.java.specialization.recommendationservice.messaging;

import com.epam.java.specialization.recommendationservice.event.RecommendationsServedEvent;
import com.epam.java.specialization.recommendationservice.event.TrackLikeChange;
import com.epam.java.specialization.recommendationservice.event.TrackSnapshot;
import com.epam.java.specialization.recommendationservice.model.TrackInfo;
import com.epam.java.specialization.recommendationservice.repository.TasteRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;

/**
 * Consumers of api-contracts/recommendation-service/asyncapi.yaml. Every write is idempotent (see
 * {@link TasteRepository}), so at-least-once delivery needs no table of processed event ids. A record that cannot
 * be read is logged and skipped; a database error is thrown, so the container's error handler retries it.
 */
@Component
public class RecommendationListeners {

    private static final Logger log = LoggerFactory.getLogger(RecommendationListeners.class);

    private final TasteRepository taste;
    private final JsonMapper json;

    public RecommendationListeners(TasteRepository taste, JsonMapper json) {
        this.taste = taste;
        this.json = json;
    }

    @KafkaListener(topics = "${events.topics.track-snapshots}", groupId = "${spring.kafka.consumer.group-id}")
    public void onTrackSnapshot(ConsumerRecord<String, String> record) {
        TrackSnapshot s = parse(record, TrackSnapshot.class);
        if (s == null || s.getTrackId() == null || s.getStatus() == null || s.getOccurredAt() == null) {
            return;
        }
        taste.saveSnapshot(new TrackInfo(s.getTrackId(), s.getArtistId() == null ? 0 : s.getArtistId(),
                s.getTitle() == null ? "" : s.getTitle(),
                s.getGenres() == null ? java.util.Set.of() : new LinkedHashSet<>(s.getGenres()),
                s.getStatus().getValue(), s.getReleaseDate()), s.getOccurredAt().toInstant());
    }

    @KafkaListener(topics = "${events.topics.track-likes}", groupId = "${spring.kafka.consumer.group-id}")
    public void onTrackLike(ConsumerRecord<String, String> record) {
        String type = header(record, "eventType");
        if (!"TrackLikedEvent".equals(type) && !"TrackUnlikedEvent".equals(type)) {
            log.warn("Skipping {} offset={} with eventType={}", record.topic(), record.offset(), type);
            return;
        }
        TrackLikeChange e = parse(record, TrackLikeChange.class);
        if (e == null || e.getUserId() == null || e.getTrackId() == null || e.getOccurredAt() == null) {
            return;
        }
        taste.saveLike(e.getUserId(), e.getTrackId(), "TrackLikedEvent".equals(type), e.getOccurredAt().toInstant());
    }

    @KafkaListener(topics = "${events.topics.feed-served}", groupId = "${spring.kafka.consumer.group-id}")
    public void onFeedServed(ConsumerRecord<String, String> record) {
        RecommendationsServedEvent e = parse(record, RecommendationsServedEvent.class);
        if (e == null || e.getUserId() == null || e.getFeedId() == null || e.getOccurredAt() == null) {
            return;
        }
        taste.saveImpressions(e.getUserId(), e.getFeedId(), e.getTrackIds(), e.getOccurredAt().toInstant());
    }

    private <T> T parse(ConsumerRecord<String, String> record, Class<T> type) {
        try {
            return json.readValue(record.value(), type);
        } catch (JacksonException | IllegalArgumentException e) {
            log.warn("Skipping unreadable {} on {} offset={}: {}", type.getSimpleName(), record.topic(),
                    record.offset(), e.getMessage());
            return null;
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}

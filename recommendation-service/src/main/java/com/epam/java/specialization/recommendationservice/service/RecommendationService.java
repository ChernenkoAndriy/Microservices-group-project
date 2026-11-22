package com.epam.java.specialization.recommendationservice.service;

import com.epam.java.specialization.recommendationservice.api.dto.ChartDto;
import com.epam.java.specialization.recommendationservice.api.dto.ChartEntryDto;
import com.epam.java.specialization.recommendationservice.api.dto.ChartPeriodDto;
import com.epam.java.specialization.recommendationservice.api.dto.RecommendationFeedDto;
import com.epam.java.specialization.recommendationservice.api.dto.RecommendedTrackDto;
import com.epam.java.specialization.recommendationservice.event.RecommendationsServedEvent;
import com.epam.java.specialization.recommendationservice.model.TrackInfo;
import com.epam.java.specialization.recommendationservice.repository.AnalyticsRepository;
import com.epam.java.specialization.recommendationservice.repository.TasteRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    public static final String FEED_SERVED = "RecommendationsServedEvent";
    static final Duration IMPRESSION_WINDOW = Duration.ofHours(12);

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final TasteRepository taste;
    private final AnalyticsRepository analytics;
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final Clock clock;
    private final String feedServedTopic;

    public RecommendationService(TasteRepository taste, AnalyticsRepository analytics,
                                 KafkaTemplate<String, String> kafka, JsonMapper json, Clock clock,
                                 @Value("${events.topics.feed-served}") String feedServedTopic) {
        this.taste = taste;
        this.analytics = analytics;
        this.kafka = kafka;
        this.json = json;
        this.clock = clock;
        this.feedServedTopic = feedServedTopic;
    }

    /**
     * Builds the feed from the local copies (snapshots, likes, plays, impressions) and returns it at once; the
     * {@code RecommendationsServedEvent} is sent afterwards without waiting, and its consumer stores the impressions.
     */
    public RecommendationFeedDto feedFor(long userId, int limit, String genre) {
        Instant now = clock.instant();
        List<FeedBuilder.Pick> picks = FeedBuilder.build(new FeedBuilder.Input(
                taste.publishedTracks(),
                taste.likedTracks(userId),
                analytics.userPlayCounts(userId),
                analytics.playCountsSince(now.minus(Duration.ofDays(30))),
                taste.impressionsSince(userId, now.minus(IMPRESSION_WINDOW)),
                genre,
                LocalDate.ofInstant(now, ZoneOffset.UTC),
                limit));
        UUID feedId = UUID.randomUUID();
        List<RecommendedTrackDto> items = new ArrayList<>();
        for (int i = 0; i < picks.size(); i++) {
            items.add(new RecommendedTrackDto(picks.get(i).trackId(), i + 1, picks.get(i).reason()));
        }
        publishServed(userId, feedId, picks.stream().map(FeedBuilder.Pick::trackId).toList(), now);
        return new RecommendationFeedDto(feedId, now.atOffset(ZoneOffset.UTC), items);
    }

    /** Ranked by plays in the period; tracks that are known and no longer published are left out. */
    public ChartDto chart(ChartPeriodDto period, String genre, int limit) {
        ChartPeriodDto p = period == null ? ChartPeriodDto.WEEK : period;
        Instant now = clock.instant();
        Instant since = switch (p) {
            case DAY -> now.minus(Duration.ofDays(1));
            case WEEK -> now.minus(Duration.ofDays(7));
            case MONTH -> now.minus(Duration.ofDays(30));
            case ALL_TIME -> null;
        };
        Map<Long, Long> plays = analytics.playCountsSince(since);
        Map<Long, String> statuses = taste.statuses();
        Map<Long, TrackInfo> published = genre == null ? Map.of() : taste.publishedTracks().stream()
                .collect(Collectors.toMap(TrackInfo::trackId, t -> t));
        List<ChartEntryDto> items = new ArrayList<>();
        for (Map.Entry<Long, Long> e : plays.entrySet()) {
            if (items.size() >= limit) {
                break;
            }
            String status = statuses.get(e.getKey());
            if (status != null && !"PUBLISHED".equals(status)) {
                continue;
            }
            if (genre != null) {
                TrackInfo t = published.get(e.getKey());
                if (t == null || !t.genres().contains(genre)) {
                    continue;
                }
            }
            items.add(new ChartEntryDto(items.size() + 1, e.getKey(), e.getValue()));
        }
        ChartDto chart = new ChartDto(p, now.atOffset(ZoneOffset.UTC), items);
        chart.setGenre(genre);
        return chart;
    }

    private void publishServed(long userId, UUID feedId, List<Long> trackIds, Instant now) {
        if (trackIds.isEmpty()) {
            return;
        }
        RecommendationsServedEvent event = new RecommendationsServedEvent(UUID.randomUUID(),
                now.atOffset(ZoneOffset.UTC), userId, feedId, trackIds);
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(feedServedTopic, String.valueOf(userId),
                    json.writeValueAsString(event));
            record.headers().add("eventType", FEED_SERVED.getBytes(StandardCharsets.UTF_8));
            kafka.send(record).whenComplete((result, error) -> {
                if (error != null) {
                    log.warn("Could not send {} for user {}: {}", FEED_SERVED, userId, error.toString());
                }
            });
        } catch (RuntimeException e) {
            // Impressions are best effort: losing one only means the same tracks may come first again.
            log.warn("Could not send {} for user {}: {}", FEED_SERVED, userId, e.toString());
        }
    }
}

package com.epam.java.specialization.recommendationservice.repository;

import com.epam.java.specialization.recommendationservice.model.TrackInfo;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ClickHouse tables filled by the Kafka consumers: a local copy of the catalog's tracks, the users' likes and the
 * served feeds (impressions). Snapshots and likes are {@code ReplacingMergeTree} tables keyed by their natural id
 * with the event time as the version, so a repeated or late event never overrides a newer state
 * (reads use {@code FINAL}). Impressions are keyed by (user, feed, track), so a repeated served event is harmless.
 */
@Repository
public class TasteRepository {

    private final JdbcTemplate jdbc;

    public TasteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS track_snapshots (
                    track_id UInt64,
                    artist_id UInt64,
                    title String,
                    genres String,
                    status String,
                    release_date String,
                    version UInt64
                ) ENGINE = ReplacingMergeTree(version)
                ORDER BY track_id
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS track_likes (
                    user_id UInt64,
                    track_id UInt64,
                    liked UInt8,
                    version UInt64
                ) ENGINE = ReplacingMergeTree(version)
                ORDER BY (user_id, track_id)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS feed_impressions (
                    user_id UInt64,
                    feed_id String,
                    track_id UInt64,
                    position UInt16,
                    served_at DateTime
                ) ENGINE = ReplacingMergeTree
                ORDER BY (user_id, feed_id, track_id)
                """);
    }

    public void saveSnapshot(TrackInfo track, Instant occurredAt) {
        jdbc.update("INSERT INTO track_snapshots (track_id, artist_id, title, genres, status, release_date, version)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                track.trackId(), track.artistId(), track.title(), String.join(",", track.genres()),
                track.status(), track.releaseDate() == null ? "" : track.releaseDate().toString(),
                occurredAt.toEpochMilli());
    }

    /** All tracks whose latest snapshot is {@code PUBLISHED}. */
    public List<TrackInfo> publishedTracks() {
        return jdbc.query("SELECT track_id, artist_id, title, genres, status, release_date"
                        + " FROM track_snapshots FINAL WHERE status = 'PUBLISHED'",
                (rs, i) -> new TrackInfo(rs.getLong("track_id"), rs.getLong("artist_id"), rs.getString("title"),
                        genres(rs.getString("genres")), rs.getString("status"), date(rs.getString("release_date"))));
    }

    /** Latest status per known track id (tracks never seen are missing from the map). */
    public Map<Long, String> statuses() {
        Map<Long, String> result = new HashMap<>();
        jdbc.query("SELECT track_id, status FROM track_snapshots FINAL",
                rs -> { result.put(rs.getLong("track_id"), rs.getString("status")); });
        return result;
    }

    public void saveLike(long userId, long trackId, boolean liked, Instant occurredAt) {
        jdbc.update("INSERT INTO track_likes (user_id, track_id, liked, version) VALUES (?, ?, ?, ?)",
                userId, trackId, liked ? 1 : 0, occurredAt.toEpochMilli());
    }

    public Set<Long> likedTracks(long userId) {
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT track_id FROM track_likes FINAL WHERE user_id = ? AND liked = 1", Long.class, userId));
    }

    public void saveImpressions(long userId, UUID feedId, List<Long> trackIds, Instant servedAt) {
        if (trackIds.isEmpty()) {
            return;
        }
        long at = servedAt.getEpochSecond();
        jdbc.batchUpdate("INSERT INTO feed_impressions (user_id, feed_id, track_id, position, served_at)"
                        + " VALUES (?, ?, ?, ?, toDateTime(?))",
                trackIds.stream().map(trackId -> new Object[]{userId, feedId.toString(), trackId,
                        trackIds.indexOf(trackId) + 1, at}).toList());
    }

    /** How often each track was shown to the user since {@code since}. */
    public Map<Long, Integer> impressionsSince(long userId, Instant since) {
        Map<Long, Integer> result = new HashMap<>();
        jdbc.query("SELECT track_id, count() AS shown FROM feed_impressions FINAL"
                        + " WHERE user_id = ? AND served_at >= toDateTime(?) GROUP BY track_id",
                rs -> { result.put(rs.getLong("track_id"), rs.getInt("shown")); },
                userId, since.getEpochSecond());
        return result;
    }

    private static Set<String> genres(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(",")).filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static LocalDate date(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }
}

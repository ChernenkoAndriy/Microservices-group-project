package com.epam.java.specialization.recommendationservice.repository;

import com.epam.java.specialization.recommendationservice.metrics.RecommendationMetrics;
import com.epam.java.specialization.recommendationservice.model.ListenEvent;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RecommendationMetrics metrics;

    public AnalyticsRepository(JdbcTemplate jdbcTemplate, RecommendationMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    @PostConstruct
    public void initTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS user_listen_events (
                user_id String,
                track_id String,
                listen_duration_seconds UInt32,
                event_time DateTime
            ) ENGINE = MergeTree()
            ORDER BY (user_id, event_time);
        """;
        jdbcTemplate.execute(sql);
    }

    public void saveEvent(ListenEvent event) {
        String sql = "INSERT INTO user_listen_events (user_id, track_id, listen_duration_seconds, event_time) VALUES (?, ?, ?, ?)";
        LocalDateTime eventTime = event.getEventTime() != null ? event.getEventTime() : LocalDateTime.now();
        metrics.recordEventSave(() -> jdbcTemplate.update(sql, event.getUserId(), event.getTrackId(),
                event.getListenDurationSeconds(), Timestamp.valueOf(eventTime)));
    }

    /** Play counts per track since {@code since} (all time when null), most played first. */
    public Map<Long, Long> playCountsSince(Instant since) {
        Map<Long, Long> result = new LinkedHashMap<>();
        String sql = "SELECT toUInt64OrZero(track_id) AS tid, count() AS plays FROM user_listen_events"
                + (since == null ? "" : " WHERE event_time >= toDateTime(?)")
                + " GROUP BY tid HAVING tid > 0 ORDER BY plays DESC, tid ASC LIMIT 1000";
        RowCallbackHandler row = rs -> result.put(rs.getLong("tid"), rs.getLong("plays"));
        if (since == null) {
            jdbcTemplate.query(sql, row);
        } else {
            jdbcTemplate.query(sql, row, since.getEpochSecond());
        }
        return result;
    }

    /** How often the user played each track. */
    public Map<Long, Long> userPlayCounts(long userId) {
        Map<Long, Long> result = new HashMap<>();
        jdbcTemplate.query("SELECT toUInt64OrZero(track_id) AS tid, count() AS plays FROM user_listen_events"
                        + " WHERE user_id = ? GROUP BY tid HAVING tid > 0",
                rs -> { result.put(rs.getLong("tid"), rs.getLong("plays")); }, String.valueOf(userId));
        return result;
    }

    public List<ListenEvent> getEventsByUser(String userId) {
        String sql = "SELECT user_id, track_id, listen_duration_seconds, event_time FROM user_listen_events WHERE user_id = ? ORDER BY event_time DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ListenEvent(
                rs.getString("user_id"),
                rs.getString("track_id"),
                rs.getLong("listen_duration_seconds"),
                rs.getTimestamp("event_time").toLocalDateTime()
        ), userId);
    }
}
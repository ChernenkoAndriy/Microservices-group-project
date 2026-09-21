package com.epam.java.specialization.recommendationservice.repository;

import com.epam.java.specialization.recommendationservice.model.ListenEvent;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
        jdbcTemplate.update(sql, event.getUserId(), event.getTrackId(), event.getListenDurationSeconds(), Timestamp.valueOf(eventTime));
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
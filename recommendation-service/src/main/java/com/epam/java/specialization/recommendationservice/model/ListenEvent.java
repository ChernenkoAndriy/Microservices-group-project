package com.epam.java.specialization.recommendationservice.model;

import java.time.LocalDateTime;

public class ListenEvent {
    private String userId;
    private String trackId;
    private Long listenDurationSeconds;
    private LocalDateTime eventTime;

    public ListenEvent() {}

    public ListenEvent(String userId, String trackId, Long listenDurationSeconds, LocalDateTime eventTime) {
        this.userId = userId;
        this.trackId = trackId;
        this.listenDurationSeconds = listenDurationSeconds;
        this.eventTime = eventTime;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTrackId() { return trackId; }
    public void setTrackId(String trackId) { this.trackId = trackId; }

    public Long getListenDurationSeconds() { return listenDurationSeconds; }
    public void setListenDurationSeconds(Long listenDurationSeconds) { this.listenDurationSeconds = listenDurationSeconds; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
}
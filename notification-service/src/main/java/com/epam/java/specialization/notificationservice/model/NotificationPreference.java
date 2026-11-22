package com.epam.java.specialization.notificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    private Long userId;

    @Column(nullable = false)
    private boolean newReleases = true;

    @Column(nullable = false)
    private boolean yearSummary = true;

    @Column(nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
    }

    public NotificationPreference(Long userId, boolean newReleases, boolean yearSummary, Instant updatedAt) {
        this.userId = userId;
        this.newReleases = newReleases;
        this.yearSummary = yearSummary;
        this.updatedAt = updatedAt;
    }

    public void replace(boolean newReleases, boolean yearSummary, Instant now) {
        this.newReleases = newReleases;
        this.yearSummary = yearSummary;
        this.updatedAt = now;
    }

    public Long getUserId() { return userId; }
    public boolean isNewReleases() { return newReleases; }
    public boolean isYearSummary() { return yearSummary; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package com.epam.java.specialization.libraryservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A track the user likes. One row per user and track. */
@Entity
@Table(name = "track_like",
        uniqueConstraints = @UniqueConstraint(name = "uk_track_like_user_track", columnNames = {"userId", "trackId"}),
        indexes = @Index(name = "idx_track_like_user_time", columnList = "userId, likedAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrackLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long trackId;

    @Column(nullable = false)
    private Instant likedAt;

    public TrackLike(Long userId, Long trackId, Instant likedAt) {
        this.userId = userId;
        this.trackId = trackId;
        this.likedAt = likedAt;
    }
}

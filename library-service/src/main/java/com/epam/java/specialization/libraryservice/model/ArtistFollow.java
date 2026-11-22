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

/** An artist the user follows. One row per user and artist. */
@Entity
@Table(name = "artist_follow",
        uniqueConstraints = @UniqueConstraint(name = "uk_artist_follow_user_artist",
                columnNames = {"userId", "artistId"}),
        indexes = @Index(name = "idx_artist_follow_user_time", columnList = "userId, followedAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long artistId;

    @Column(nullable = false)
    private Instant followedAt;

    public ArtistFollow(Long userId, Long artistId, Instant followedAt) {
        this.userId = userId;
        this.artistId = artistId;
        this.followedAt = followedAt;
    }
}

package com.epam.java.specialization.libraryservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One track on a playlist. A track appears at most once per playlist. */
@Entity
@Table(name = "playlist_track",
        uniqueConstraints = @UniqueConstraint(name = "uk_playlist_track", columnNames = {"playlist_id", "trackId"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaylistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @Column(nullable = false)
    private Long trackId;

    @Setter
    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private Instant addedAt;

    PlaylistEntry(Playlist playlist, Long trackId, int position, Instant addedAt) {
        this.playlist = playlist;
        this.trackId = trackId;
        this.position = position;
        this.addedAt = addedAt;
    }
}

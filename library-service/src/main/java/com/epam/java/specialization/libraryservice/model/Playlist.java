package com.epam.java.specialization.libraryservice.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A private playlist of one user. Its tracks keep a one-based, gap-free {@code position}. */
@Entity
@Table(name = "playlist", indexes = @Index(name = "idx_playlist_owner_updated", columnList = "ownerId, updatedAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Playlist {

    public static final int MAX_TRACKS = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Setter
    @Column(nullable = false, length = 100)
    private String name;

    @Setter
    @Column(length = 500)
    private String description;

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PlaylistEntry> entries = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public Playlist(Long ownerId, String name, String description, Instant now) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(Long userId) {
        return ownerId.equals(userId);
    }

    public Optional<PlaylistEntry> find(Long trackId) {
        return entries.stream().filter(entry -> entry.getTrackId().equals(trackId)).findFirst();
    }

    /** Adds the track at the end. */
    public void add(Long trackId, Instant now) {
        entries.add(new PlaylistEntry(this, trackId, entries.size() + 1, now));
        touch(now);
    }

    /** Removes the track; the tracks after it move up by one. */
    public boolean remove(Long trackId, Instant now) {
        Optional<PlaylistEntry> entry = find(trackId);
        if (entry.isEmpty()) {
            return false;
        }
        entries.remove(entry.get());
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setPosition(i + 1);
        }
        touch(now);
        return true;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }
}

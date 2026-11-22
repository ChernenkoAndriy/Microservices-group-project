package com.epam.java.specialization.trackservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/** An album, EP, single or compilation of one artist. Tracks point to it ({@link Track#getAlbum()}). */
@Entity
@Table(name = "album", indexes = @Index(name = "idx_album_artist", columnList = "artistId"))
@BatchSize(size = 100)
@Getter
@Setter
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Long artistId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlbumType type;

    private LocalDate releaseDate;

    @Column(length = 2048)
    private String coverUrl;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public boolean isOwnedBy(Long userId) {
        return artistId.equals(userId);
    }
}

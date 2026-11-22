package com.epam.java.specialization.notificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** One user following one artist, kept from follow/unfollow events so a release needs no call to library-service. */
@Entity
@Table(name = "artist_followers", indexes = @Index(name = "idx_artist_followers_artist", columnList = "artistId"))
public class ArtistFollower {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false)
    private Long artistId;

    @Column(nullable = false)
    private Long userId;

    protected ArtistFollower() {
    }

    public ArtistFollower(Long artistId, Long userId) {
        this.id = key(artistId, userId);
        this.artistId = artistId;
        this.userId = userId;
    }

    public static String key(Long artistId, Long userId) {
        return artistId + ":" + userId;
    }

    public Long getArtistId() { return artistId; }
    public Long getUserId() { return userId; }
}

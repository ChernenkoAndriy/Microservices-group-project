package com.epam.java.specialization.trackservice.repository;

import com.epam.java.specialization.trackservice.model.Genre;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.Locale;

public final class TrackSpecifications {

    private TrackSpecifications() {
    }

    public static Specification<Track> hasStatus(TrackStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Track> byArtist(Long artistId) {
        return (root, query, cb) -> artistId == null ? null : cb.equal(root.get("artistId"), artistId);
    }

    public static Specification<Track> idIn(Collection<Long> ids) {
        return (root, query, cb) -> ids == null || ids.isEmpty() ? null : root.get("id").in(ids);
    }

    public static Specification<Track> titleContains(String q) {
        return (root, query, cb) -> q == null || q.isBlank() ? null
                : cb.like(cb.lower(root.get("title")), "%" + q.toLowerCase(Locale.ROOT) + "%");
    }

    public static Specification<Track> hasGenre(String slug) {
        return (root, query, cb) -> {
            if (slug == null) {
                return null;
            }
            query.distinct(true);
            Join<Track, Genre> genres = root.join("genres");
            return cb.equal(genres.get("slug"), slug);
        };
    }
}

package com.epam.java.specialization.libraryservice.service;

import com.epam.java.specialization.libraryservice.api.dto.LikeDto;
import com.epam.java.specialization.libraryservice.api.dto.LikePageDto;
import com.epam.java.specialization.libraryservice.client.CatalogDirectory;
import com.epam.java.specialization.libraryservice.messaging.LibraryEvents;
import com.epam.java.specialization.libraryservice.model.TrackLike;
import com.epam.java.specialization.libraryservice.repository.TrackLikeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "likedAt", "id");

    private final TrackLikeRepository likeRepository;
    private final CatalogDirectory catalog;
    private final LibraryEvents events;
    private final Clock clock;

    /** Result of an idempotent like: {@code created} is false when the track was already liked. */
    public record LikeResult(LikeDto like, boolean created) {
    }

    @Transactional(readOnly = true)
    public LikePageDto list(Long userId, Set<Long> trackIds, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<TrackLike> likes = trackIds == null || trackIds.isEmpty()
                ? likeRepository.findByUserId(userId, pageable)
                : likeRepository.findByUserIdAndTrackIdIn(userId, trackIds, pageable);
        return new LikePageDto(likes.map(LikeService::toDto).getContent(), PageMapping.toMetadata(likes));
    }

    /** A new like is stored and emits {@code TrackLikedEvent}; liking again changes nothing. */
    @Transactional
    public LikeResult like(Long userId, Long trackId) {
        var existing = likeRepository.findByUserIdAndTrackId(userId, trackId);
        if (existing.isPresent()) {
            return new LikeResult(toDto(existing.get()), false);
        }
        catalog.requirePublishedTrack(trackId);
        Instant now = clock.instant();
        TrackLike like = likeRepository.saveAndFlush(new TrackLike(userId, trackId, now));
        events.trackLiked(userId, trackId, now);
        log.debug("User id={} liked track id={}", userId, trackId);
        return new LikeResult(toDto(like), true);
    }

    /** Emits {@code TrackUnlikedEvent} only when a like was actually removed. */
    @Transactional
    public void unlike(Long userId, Long trackId) {
        likeRepository.findByUserIdAndTrackId(userId, trackId).ifPresent(like -> {
            likeRepository.delete(like);
            events.trackUnliked(userId, trackId, clock.instant());
            log.debug("User id={} unliked track id={}", userId, trackId);
        });
    }

    private static LikeDto toDto(TrackLike like) {
        return new LikeDto(like.getTrackId(), like.getLikedAt().atOffset(ZoneOffset.UTC));
    }
}

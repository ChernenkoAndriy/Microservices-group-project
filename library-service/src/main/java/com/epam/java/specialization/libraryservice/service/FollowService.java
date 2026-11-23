package com.epam.java.specialization.libraryservice.service;

import com.epam.java.specialization.libraryservice.api.dto.FollowDto;
import com.epam.java.specialization.libraryservice.api.dto.FollowPageDto;
import com.epam.java.specialization.libraryservice.client.CatalogDirectory;
import com.epam.java.specialization.libraryservice.exception.LibraryException;
import com.epam.java.specialization.libraryservice.messaging.LibraryEvents;
import com.epam.java.specialization.libraryservice.model.ArtistFollow;
import com.epam.java.specialization.libraryservice.repository.ArtistFollowRepository;
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
public class FollowService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "followedAt", "id");

    private final ArtistFollowRepository followRepository;
    private final CatalogDirectory catalog;
    private final LibraryEvents events;
    private final Clock clock;

    /** Result of an idempotent follow: {@code created} is false when the artist was already followed. */
    public record FollowResult(FollowDto follow, boolean created) {
    }

    @Transactional(readOnly = true)
    public FollowPageDto list(Long userId, Set<Long> artistIds, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<ArtistFollow> follows = artistIds == null || artistIds.isEmpty()
                ? followRepository.findByUserId(userId, pageable)
                : followRepository.findByUserIdAndArtistIdIn(userId, artistIds, pageable);
        return new FollowPageDto(follows.map(FollowService::toDto).getContent(), PageMapping.toMetadata(follows));
    }

    /** A new follow is stored and emits {@code ArtistFollowedEvent}; following again changes nothing. */
    @Transactional
    public FollowResult follow(Long userId, Long artistId) {
        if (userId.equals(artistId)) {
            throw LibraryException.conflict("SELF_FOLLOW_FORBIDDEN", "Artists cannot follow themselves");
        }
        var existing = followRepository.findByUserIdAndArtistId(userId, artistId);
        if (existing.isPresent()) {
            return new FollowResult(toDto(existing.get()), false);
        }
        catalog.requireArtist(artistId);
        Instant now = clock.instant();
        ArtistFollow follow = followRepository.saveAndFlush(new ArtistFollow(userId, artistId, now));
        events.artistFollowed(userId, artistId, now);
        log.debug("User id={} followed artist id={}", userId, artistId);
        return new FollowResult(toDto(follow), true);
    }

    /** Emits {@code ArtistUnfollowedEvent} only when a follow was actually removed. */
    @Transactional
    public void unfollow(Long userId, Long artistId) {
        followRepository.findByUserIdAndArtistId(userId, artistId).ifPresent(follow -> {
            followRepository.delete(follow);
            events.artistUnfollowed(userId, artistId, clock.instant());
            log.debug("User id={} unfollowed artist id={}", userId, artistId);
        });
    }

    private static FollowDto toDto(ArtistFollow follow) {
        return new FollowDto(follow.getArtistId(), follow.getFollowedAt().atOffset(ZoneOffset.UTC));
    }
}

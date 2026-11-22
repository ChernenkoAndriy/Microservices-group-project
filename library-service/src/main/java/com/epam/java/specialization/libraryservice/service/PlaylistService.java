package com.epam.java.specialization.libraryservice.service;

import com.epam.java.specialization.libraryservice.api.dto.CreatePlaylistRequestDto;
import com.epam.java.specialization.libraryservice.api.dto.PlaylistDto;
import com.epam.java.specialization.libraryservice.api.dto.PlaylistSummaryDto;
import com.epam.java.specialization.libraryservice.api.dto.PlaylistSummaryPageDto;
import com.epam.java.specialization.libraryservice.api.dto.PlaylistTrackDto;
import com.epam.java.specialization.libraryservice.api.dto.UpdatePlaylistRequestDto;
import com.epam.java.specialization.libraryservice.client.CatalogDirectory;
import com.epam.java.specialization.libraryservice.exception.LibraryException;
import com.epam.java.specialization.libraryservice.model.Playlist;
import com.epam.java.specialization.libraryservice.repository.PlaylistRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Private playlists (api-contracts/library-service/openapi.yaml, tag Playlists). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistService {

    public static final int MAX_PLAYLISTS = 200;
    private static final Sort RECENTLY_CHANGED_FIRST = Sort.by(Sort.Direction.DESC, "updatedAt", "id");

    private final PlaylistRepository playlistRepository;
    private final CatalogDirectory catalog;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PlaylistSummaryPageDto list(Long userId, int page, int size) {
        Page<Playlist> playlists = playlistRepository.findByOwnerId(userId,
                PageRequest.of(page, size, RECENTLY_CHANGED_FIRST));
        Map<Long, Integer> counts = new HashMap<>();
        if (!playlists.isEmpty()) {
            for (Object[] row : playlistRepository.countTracks(playlists.map(Playlist::getId).getContent())) {
                counts.put((Long) row[0], ((Number) row[1]).intValue());
            }
        }
        List<PlaylistSummaryDto> items = playlists
                .map(p -> toSummary(p, counts.getOrDefault(p.getId(), 0))).getContent();
        return new PlaylistSummaryPageDto(items, PageMapping.toMetadata(playlists));
    }

    @Transactional
    public PlaylistDto create(Long userId, CreatePlaylistRequestDto request) {
        if (playlistRepository.countByOwnerId(userId) >= MAX_PLAYLISTS) {
            throw LibraryException.conflict("PLAYLIST_LIMIT_REACHED",
                    "A user can have at most " + MAX_PLAYLISTS + " playlists");
        }
        Playlist playlist = playlistRepository.saveAndFlush(
                new Playlist(userId, request.getName(), request.getDescription(), clock.instant()));
        log.debug("User id={} created playlist id={}", userId, playlist.getId());
        return toDto(playlist);
    }

    @Transactional(readOnly = true)
    public PlaylistDto get(Long userId, Long playlistId) {
        return toDto(owned(userId, playlistId));
    }

    @Transactional
    public PlaylistDto update(Long userId, Long playlistId, UpdatePlaylistRequestDto request) {
        if (request.getName() == null && request.getDescription() == null) {
            throw LibraryException.badRequest("EMPTY_UPDATE", "At least one field must be present");
        }
        Playlist playlist = owned(userId, playlistId);
        if (request.getName() != null) {
            playlist.setName(request.getName());
        }
        if (request.getDescription() != null) {
            playlist.setDescription(request.getDescription());
        }
        playlist.touch(clock.instant());
        return toDto(playlistRepository.saveAndFlush(playlist));
    }

    /** Idempotent: a missing playlist, or one of another user, is left alone. */
    @Transactional
    public void delete(Long userId, Long playlistId) {
        playlistRepository.findById(playlistId)
                .filter(playlist -> playlist.isOwnedBy(userId))
                .ifPresent(playlistRepository::delete);
    }

    /** Idempotent: a track already on the playlist keeps its position. */
    @Transactional
    public PlaylistDto addTrack(Long userId, Long playlistId, Long trackId) {
        Playlist playlist = owned(userId, playlistId);
        if (playlist.find(trackId).isPresent()) {
            return toDto(playlist);
        }
        if (playlist.getEntries().size() >= Playlist.MAX_TRACKS) {
            throw LibraryException.conflict("PLAYLIST_FULL",
                    "A playlist holds at most " + Playlist.MAX_TRACKS + " tracks");
        }
        catalog.requirePublishedTrack(trackId);
        playlist.add(trackId, clock.instant());
        return toDto(playlistRepository.saveAndFlush(playlist));
    }

    @Transactional
    public void removeTrack(Long userId, Long playlistId, Long trackId) {
        Playlist playlist = owned(userId, playlistId);
        Instant now = clock.instant();
        if (playlist.remove(trackId, now)) {
            playlistRepository.saveAndFlush(playlist);
        }
    }

    private Playlist owned(Long userId, Long playlistId) {
        return playlistRepository.findById(playlistId)
                .filter(playlist -> playlist.isOwnedBy(userId))
                .orElseThrow(() -> LibraryException.notFound("PLAYLIST_NOT_FOUND",
                        "Playlist " + playlistId + " does not exist"));
    }

    private static PlaylistDto toDto(Playlist playlist) {
        List<PlaylistTrackDto> tracks = playlist.getEntries().stream()
                .map(entry -> new PlaylistTrackDto(entry.getTrackId(), entry.getPosition(),
                        entry.getAddedAt().atOffset(ZoneOffset.UTC)))
                .toList();
        PlaylistDto dto = new PlaylistDto(playlist.getId(), playlist.getName(), tracks.size(), tracks,
                playlist.getCreatedAt().atOffset(ZoneOffset.UTC), playlist.getUpdatedAt().atOffset(ZoneOffset.UTC));
        dto.setDescription(playlist.getDescription());
        return dto;
    }

    private static PlaylistSummaryDto toSummary(Playlist playlist, int trackCount) {
        PlaylistSummaryDto dto = new PlaylistSummaryDto(playlist.getId(), playlist.getName(), trackCount,
                playlist.getCreatedAt().atOffset(ZoneOffset.UTC), playlist.getUpdatedAt().atOffset(ZoneOffset.UTC));
        dto.setDescription(playlist.getDescription());
        return dto;
    }
}

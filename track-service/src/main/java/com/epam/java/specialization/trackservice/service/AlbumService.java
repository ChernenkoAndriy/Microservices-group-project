package com.epam.java.specialization.trackservice.service;

import com.epam.java.specialization.trackservice.api.dto.AlbumDto;
import com.epam.java.specialization.trackservice.api.dto.AlbumSummaryDto;
import com.epam.java.specialization.trackservice.api.dto.AlbumSummaryPageDto;
import com.epam.java.specialization.trackservice.api.dto.AlbumTypeDto;
import com.epam.java.specialization.trackservice.api.dto.CreateAlbumRequestDto;
import com.epam.java.specialization.trackservice.client.ArtistDirectory;
import com.epam.java.specialization.trackservice.client.ArtistProfiles;
import com.epam.java.specialization.trackservice.exception.CatalogException;
import com.epam.java.specialization.trackservice.mapper.DateTimeMapping;
import com.epam.java.specialization.trackservice.mapper.TrackMapper;
import com.epam.java.specialization.trackservice.model.Album;
import com.epam.java.specialization.trackservice.model.AlbumType;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import com.epam.java.specialization.trackservice.repository.AlbumRepository;
import com.epam.java.specialization.trackservice.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final TrackRepository trackRepository;
    private final ArtistDirectory artistDirectory;
    private final TrackMapper trackMapper;

    @Transactional
    public AlbumDto create(Long artistId, CreateAlbumRequestDto request) {
        Album album = new Album();
        album.setTitle(request.getTitle());
        album.setArtistId(artistId);
        album.setType(AlbumType.valueOf(request.getType().getValue()));
        album.setReleaseDate(request.getReleaseDate());
        Album saved = albumRepository.saveAndFlush(album);
        log.debug("Artist id={} created album id={}", artistId, saved.getId());
        return toDto(saved, List.of(), artistDirectory.findProfile(artistId));
    }

    /**
     * An album with its published tracks. An album without published tracks is visible only to its owner and to
     * administrators; everybody else gets 404, as for unpublished tracks.
     */
    @Transactional(readOnly = true)
    public AlbumDto get(Long albumId, Long callerId, boolean callerIsAdmin) {
        Album album = albumRepository.findById(albumId).orElseThrow(() -> albumNotFound(albumId));
        List<Track> tracks = trackRepository.findByAlbumIdAndStatusOrderByTrackNumberAscIdAsc(
                albumId, TrackStatus.PUBLISHED);
        if (tracks.isEmpty() && !callerIsAdmin && !album.isOwnedBy(callerId == null ? -1L : callerId)) {
            throw albumNotFound(albumId);
        }
        return toDto(album, tracks, artistDirectory.findProfile(album.getArtistId()));
    }

    /** Albums with at least one published track, newest release first. */
    @Transactional(readOnly = true)
    public AlbumSummaryPageDto listArtistAlbums(Long artistId, int page, int size) {
        ArtistProfiles artists = artistDirectory.findProfile(artistId);
        if (artists.available() && !artists.isArtist(artistId)) {
            throw ArtistService.artistNotFound(artistId);
        }
        Sort sort = Sort.by(Sort.Order.desc("releaseDate").nullsLast(), Sort.Order.desc("id"));
        Page<Album> albums = albumRepository.findByArtistWithTracksIn(
                artistId, TrackStatus.PUBLISHED, PageRequest.of(page, size, sort));
        Map<Long, Integer> counts = new HashMap<>();
        if (!albums.isEmpty()) {
            for (Object[] row : albumRepository.countTracks(
                    albums.map(Album::getId).getContent(), TrackStatus.PUBLISHED)) {
                counts.put((Long) row[0], ((Number) row[1]).intValue());
            }
        }
        List<AlbumSummaryDto> items = albums.map(album -> toSummary(album, counts.getOrDefault(album.getId(), 0),
                artists)).getContent();
        return new AlbumSummaryPageDto(items, trackMapper.toPageMetadata(albums));
    }

    private AlbumDto toDto(Album album, List<Track> tracks, ArtistProfiles artists) {
        AlbumDto dto = new AlbumDto(album.getId(), album.getTitle(),
                trackMapper.toArtistRef(album.getArtistId(), artists), AlbumTypeDto.fromValue(album.getType().name()),
                tracks.size(), tracks.stream().map(track -> trackMapper.toDto(track, artists)).toList(),
                DateTimeMapping.toOffsetDateTime(album.getCreatedAt()));
        dto.setReleaseDate(album.getReleaseDate());
        dto.setCoverUrl(album.getCoverUrl());
        return dto;
    }

    private AlbumSummaryDto toSummary(Album album, int trackCount, ArtistProfiles artists) {
        AlbumSummaryDto dto = new AlbumSummaryDto(album.getId(), album.getTitle(),
                trackMapper.toArtistRef(album.getArtistId(), artists), AlbumTypeDto.fromValue(album.getType().name()),
                trackCount);
        dto.setReleaseDate(album.getReleaseDate());
        dto.setCoverUrl(album.getCoverUrl());
        return dto;
    }

    static CatalogException albumNotFound(Long albumId) {
        return CatalogException.notFound("ALBUM_NOT_FOUND", "Album " + albumId + " does not exist");
    }
}

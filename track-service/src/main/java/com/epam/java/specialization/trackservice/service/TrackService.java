package com.epam.java.specialization.trackservice.service;

import com.epam.java.specialization.trackservice.api.dto.CreateTrackRequestDto;
import com.epam.java.specialization.trackservice.api.dto.TrackDto;
import com.epam.java.specialization.trackservice.api.dto.TrackPageDto;
import com.epam.java.specialization.trackservice.api.dto.TrackSortDto;
import com.epam.java.specialization.trackservice.client.ArtistDirectory;
import com.epam.java.specialization.trackservice.client.ArtistProfiles;
import com.epam.java.specialization.trackservice.exception.CatalogException;
import com.epam.java.specialization.trackservice.mapper.TrackMapper;
import com.epam.java.specialization.trackservice.model.Genre;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import com.epam.java.specialization.trackservice.repository.GenreRepository;
import com.epam.java.specialization.trackservice.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.java.specialization.trackservice.repository.TrackSpecifications.byArtist;
import static com.epam.java.specialization.trackservice.repository.TrackSpecifications.hasGenre;
import static com.epam.java.specialization.trackservice.repository.TrackSpecifications.hasStatus;
import static com.epam.java.specialization.trackservice.repository.TrackSpecifications.idIn;
import static com.epam.java.specialization.trackservice.repository.TrackSpecifications.titleContains;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackService {

    private final TrackRepository trackRepository;
    private final GenreRepository genreRepository;
    private final ArtistDirectory artistDirectory;
    private final Clock clock;

    @Transactional
    public TrackDto create(Long artistId, CreateTrackRequestDto request) {
        if (request.getAlbumId() != null) {
            throw CatalogException.unprocessable("ALBUM_NOT_USABLE", "Album " + request.getAlbumId() + " does not exist");
        }
        if (request.getTrackNumber() != null) {
            throw CatalogException.badRequest("TRACK_NUMBER_WITHOUT_ALBUM", "trackNumber requires albumId");
        }

        Track track = new Track();
        track.setTitle(request.getTitle());
        track.setArtistId(artistId);
        track.setGenres(resolveGenres(request.getGenreSlugs()));
        track.setExplicit(Boolean.TRUE.equals(request.getExplicit()));
        track.setReleaseDate(request.getReleaseDate());
        track.setStatus(TrackStatus.AWAITING_AUDIO);
        Track saved = trackRepository.saveAndFlush(track);
        log.debug("Artist id={} created track id={}", artistId, saved.getId());
        return TrackMapper.toDto(saved, artistDirectory.findProfile(artistId));
    }

    @Transactional(readOnly = true)
    public TrackDto get(Long trackId, Long callerId, boolean callerIsAdmin) {
        Track track = trackRepository.findById(trackId)
                .filter(t -> t.isPublished() || callerIsAdmin || t.getArtistId().equals(callerId))
                .orElseThrow(() -> trackNotFound(trackId));
        return TrackMapper.toDto(track, artistDirectory.findProfile(track.getArtistId()));
    }

    @Transactional(readOnly = true)
    public TrackPageDto search(String q, String genre, Long artistId, Set<Long> ids, TrackSortDto sort,
                               int page, int size) {
        if (ids != null && size < ids.size()) {
            throw CatalogException.badRequest("PAGE_TOO_SMALL_FOR_IDS", "size must be at least the number of ids");
        }
        Specification<Track> filter = Specification.allOf(
                hasStatus(TrackStatus.PUBLISHED), titleContains(q), hasGenre(genre), byArtist(artistId), idIn(ids));
        return toPage(trackRepository.findAll(filter, PageRequest.of(page, size, toSort(sort))));
    }

    @Transactional(readOnly = true)
    public TrackPageDto listArtistTracks(Long artistId, TrackSortDto sort, int page, int size) {
        Page<Track> tracks = trackRepository.findAll(
                Specification.allOf(hasStatus(TrackStatus.PUBLISHED), byArtist(artistId)),
                PageRequest.of(page, size, toSort(sort == TrackSortDto.RELEVANCE ? null : sort)));
        ArtistProfiles artists = artistDirectory.findProfile(artistId);
        if (artists.available() && !artists.isArtist(artistId)) {
            throw ArtistService.artistNotFound(artistId);
        }
        return TrackMapper.toPage(tracks, artists);
    }

    @Transactional(readOnly = true)
    public TrackPageDto listMyTracks(Long artistId, TrackStatus status, int page, int size) {
        Page<Track> tracks = trackRepository.findAll(
                Specification.allOf(byArtist(artistId), hasStatus(status)),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return TrackMapper.toPage(tracks, artistDirectory.findProfile(artistId));
    }

    @Transactional
    public void publish(Long trackId, int durationSeconds) {
        Track track = trackRepository.findById(trackId).orElseThrow(() -> trackNotFound(trackId));
        track.setDurationSeconds(durationSeconds);
        if (!track.isPublished()) {
            track.setStatus(TrackStatus.PUBLISHED);
            if (track.getPublishedAt() == null) {
                track.setPublishedAt(clock.instant());
            }
            if (track.getReleaseDate() == null) {
                track.setReleaseDate(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
            }
        }
        log.debug("Published track id={}", trackId);
    }

    private TrackPageDto toPage(Page<Track> tracks) {
        Set<Long> artistIds = tracks.stream().map(Track::getArtistId).collect(Collectors.toSet());
        ArtistProfiles artists = artistIds.isEmpty()
                ? new ArtistProfiles(Map.of(), true)
                : artistDirectory.findProfiles(artistIds);
        return TrackMapper.toPage(tracks, artists);
    }

    private List<Genre> resolveGenres(Set<String> slugs) {
        Map<String, Genre> known = genreRepository.findAllById(slugs).stream()
                .collect(Collectors.toMap(Genre::getSlug, Function.identity()));
        List<Genre> genres = new ArrayList<>();
        for (String slug : slugs) {
            Genre genre = known.get(slug);
            if (genre == null) {
                throw CatalogException.unprocessable("UNKNOWN_GENRE", "Unknown genre: " + slug);
            }
            genres.add(genre);
        }
        return genres;
    }

    private static Sort toSort(TrackSortDto sort) {
        if (sort == TrackSortDto.OLDEST) {
            return Sort.by(Sort.Direction.ASC, "createdAt", "id");
        }
        if (sort == TrackSortDto.TITLE) {
            return Sort.by(Sort.Direction.ASC, "title", "id");
        }
        return Sort.by(Sort.Direction.DESC, "createdAt", "id");
    }

    static CatalogException trackNotFound(Long trackId) {
        return CatalogException.notFound("TRACK_NOT_FOUND", "Track " + trackId + " does not exist");
    }
}

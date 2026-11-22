package com.epam.java.specialization.trackservice.service;

import com.epam.java.specialization.trackservice.api.dto.CreateTrackRequestDto;
import com.epam.java.specialization.trackservice.api.dto.TrackDto;
import com.epam.java.specialization.trackservice.api.dto.TrackPageDto;
import com.epam.java.specialization.trackservice.api.dto.TrackSortDto;
import com.epam.java.specialization.trackservice.client.ArtistDirectory;
import com.epam.java.specialization.trackservice.client.ArtistProfiles;
import com.epam.java.specialization.trackservice.exception.CatalogException;
import com.epam.java.specialization.trackservice.internal.api.dto.InternalTrackInfoDto;
import com.epam.java.specialization.trackservice.api.dto.UpdateTrackRequestDto;
import com.epam.java.specialization.trackservice.mapper.TrackMapper;
import com.epam.java.specialization.trackservice.messaging.TrackEvents;
import com.epam.java.specialization.trackservice.model.Album;
import com.epam.java.specialization.trackservice.model.Genre;
import com.epam.java.specialization.trackservice.repository.AlbumRepository;
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
import java.util.LinkedHashMap;
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
    private final AlbumRepository albumRepository;
    private final TrackEvents trackEvents;
    private final GenreRepository genreRepository;
    private final ArtistDirectory artistDirectory;
    private final Clock clock;
    private final TrackMapper trackMapper;

    @Transactional
    public TrackDto create(Long artistId, CreateTrackRequestDto request) {
        Album album = null;
        if (request.getAlbumId() != null) {
            album = usableAlbum(request.getAlbumId(), artistId);
            if (request.getTrackNumber() == null) {
                throw CatalogException.badRequest("TRACK_NUMBER_REQUIRED", "trackNumber is required with albumId");
            }
            if (trackRepository.existsByAlbumIdAndTrackNumber(album.getId(), request.getTrackNumber())) {
                throw trackNumberTaken(album.getId(), request.getTrackNumber());
            }
        } else if (request.getTrackNumber() != null) {
            throw CatalogException.badRequest("TRACK_NUMBER_WITHOUT_ALBUM", "trackNumber requires albumId");
        }

        Track track = new Track();
        track.setTitle(request.getTitle());
        track.setArtistId(artistId);
        track.setGenres(resolveGenres(request.getGenreSlugs()));
        track.setAlbum(album);
        track.setTrackNumber(request.getTrackNumber());
        track.setExplicit(Boolean.TRUE.equals(request.getExplicit()));
        track.setReleaseDate(request.getReleaseDate());
        track.setStatus(TrackStatus.AWAITING_AUDIO);
        Track saved = trackRepository.saveAndFlush(track);
        trackEvents.snapshot(saved);
        log.debug("Artist id={} created track id={}", artistId, saved.getId());
        return trackMapper.toDto(saved, artistDirectory.findProfile(artistId));
    }

    /**
     * Partial update by the owning artist. {@code genreSlugs}, if present, replaces the list. Moving the track to
     * an album needs {@code trackNumber} too; {@code trackNumber} alone renumbers the track within its album.
     */
    @Transactional
    public TrackDto update(Long trackId, Long artistId, UpdateTrackRequestDto request) {
        boolean hasGenres = request.getGenreSlugs() != null && !request.getGenreSlugs().isEmpty();
        if (request.getTitle() == null && !hasGenres && request.getAlbumId() == null
                && request.getTrackNumber() == null && request.getExplicit() == null
                && request.getReleaseDate() == null) {
            throw CatalogException.badRequest("EMPTY_UPDATE", "At least one field must be present");
        }
        Track track = ownedTrack(trackId, artistId);

        if (request.getAlbumId() != null) {
            Album album = usableAlbum(request.getAlbumId(), artistId);
            if (request.getTrackNumber() == null) {
                throw CatalogException.badRequest("TRACK_NUMBER_REQUIRED", "trackNumber is required with albumId");
            }
            track.setAlbum(album);
        } else if (request.getTrackNumber() != null && track.getAlbum() == null) {
            throw CatalogException.badRequest("TRACK_NUMBER_WITHOUT_ALBUM", "trackNumber requires an album");
        }
        if (request.getTrackNumber() != null) {
            if (trackRepository.existsByAlbumIdAndTrackNumberAndIdNot(
                    track.getAlbum().getId(), request.getTrackNumber(), track.getId())) {
                throw trackNumberTaken(track.getAlbum().getId(), request.getTrackNumber());
            }
            track.setTrackNumber(request.getTrackNumber());
        }
        if (request.getTitle() != null) {
            track.setTitle(request.getTitle());
        }
        if (hasGenres) {
            track.setGenres(resolveGenres(request.getGenreSlugs()));
        }
        if (request.getExplicit() != null) {
            track.setExplicit(request.getExplicit());
        }
        if (request.getReleaseDate() != null) {
            track.setReleaseDate(request.getReleaseDate());
        }
        Track saved = trackRepository.saveAndFlush(track);
        trackEvents.snapshot(saved);
        log.debug("Artist id={} updated track id={}", artistId, trackId);
        return trackMapper.toDto(saved, artistDirectory.findProfile(artistId));
    }

    /**
     * A track that was never published is deleted; a published one becomes {@code UNPUBLISHED}, so likes and
     * statistics keep a valid reference.
     */
    @Transactional
    public void delete(Long trackId, Long callerId, boolean callerIsAdmin) {
        Track track = trackRepository.findById(trackId).orElseThrow(() -> trackNotFound(trackId));
        if (!callerIsAdmin && !track.getArtistId().equals(callerId)) {
            throw CatalogException.forbidden("Only the owner or an administrator can remove this track");
        }
        if (track.getPublishedAt() == null) {
            trackEvents.snapshot(track, TrackStatus.UNPUBLISHED);
            trackRepository.delete(track);
            log.debug("Deleted track id={}", trackId);
        } else {
            track.setStatus(TrackStatus.UNPUBLISHED);
            trackEvents.snapshot(track);
            log.debug("Unpublished track id={}", trackId);
        }
    }

    @Transactional(readOnly = true)
    public TrackDto get(Long trackId, Long callerId, boolean callerIsAdmin) {
        Track track = trackRepository.findById(trackId)
                .filter(t -> t.isPublished() || callerIsAdmin || t.getArtistId().equals(callerId))
                .orElseThrow(() -> trackNotFound(trackId));
        return trackMapper.toDto(track, artistDirectory.findProfile(track.getArtistId()));
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
        return trackMapper.toPage(tracks, artists);
    }

    @Transactional(readOnly = true)
    public TrackPageDto listMyTracks(Long artistId, TrackStatus status, int page, int size) {
        Page<Track> tracks = trackRepository.findAll(
                Specification.allOf(byArtist(artistId), hasStatus(status)),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return trackMapper.toPage(tracks, artistDirectory.findProfile(artistId));
    }

    @Transactional(readOnly = true)
    public InternalTrackInfoDto getInfo(Long trackId) {
        return trackRepository.findById(trackId)
                .map(trackMapper::toInternalInfo)
                .orElseThrow(() -> trackNotFound(trackId));
    }

    /** A track in any status, for internal callers (gRPC). */
    @Transactional(readOnly = true)
    public Track getTrack(Long trackId) {
        return trackRepository.findById(trackId).orElseThrow(() -> trackNotFound(trackId));
    }

    /** Every track of the artist in any status, oldest first, for internal callers (gRPC). */
    @Transactional(readOnly = true)
    public List<Track> listAllArtistTracks(Long artistId) {
        return trackRepository.findByArtistIdOrderByCreatedAtAscIdAsc(artistId);
    }

    /**
     * Adds plays to the tracks' counters, all or nothing: an unknown track rolls back the whole batch.
     *
     * @param playsByTrack plays to add per track id
     * @return the counter of every track after the update, in the order of {@code playsByTrack}
     */
    @Transactional
    public Map<Long, Long> addPlays(Map<Long, Integer> playsByTrack) {
        Map<Long, Long> totals = new LinkedHashMap<>();
        playsByTrack.forEach((trackId, plays) -> {
            if (trackRepository.addPlays(trackId, plays) == 0) {
                throw trackNotFound(trackId);
            }
            totals.put(trackId, trackRepository.findPlayCount(trackId));
        });
        log.debug("Added plays {}", playsByTrack);
        return totals;
    }

    @Transactional
    public void publish(Long trackId, int durationSeconds) {
        Track track = trackRepository.findById(trackId).orElseThrow(() -> trackNotFound(trackId));
        track.setDurationSeconds(durationSeconds);
        boolean firstPublish = false;
        if (!track.isPublished()) {
            track.setStatus(TrackStatus.PUBLISHED);
            if (track.getPublishedAt() == null) {
                track.setPublishedAt(clock.instant());
                firstPublish = true;
            }
            if (track.getReleaseDate() == null) {
                track.setReleaseDate(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
            }
        }
        trackEvents.snapshot(track);
        if (firstPublish) {
            trackEvents.published(track);
        }
        log.debug("Published track id={}", trackId);
    }

    private Track ownedTrack(Long trackId, Long artistId) {
        Track track = trackRepository.findById(trackId).orElseThrow(() -> trackNotFound(trackId));
        if (!track.getArtistId().equals(artistId)) {
            throw CatalogException.forbidden("Only the owner can change this track");
        }
        return track;
    }

    private Album usableAlbum(Long albumId, Long artistId) {
        return albumRepository.findById(albumId)
                .filter(album -> album.isOwnedBy(artistId))
                .orElseThrow(() -> CatalogException.unprocessable("ALBUM_NOT_USABLE",
                        "Album " + albumId + " does not exist or belongs to another artist"));
    }

    private static CatalogException trackNumberTaken(Long albumId, Integer trackNumber) {
        return CatalogException.conflict("TRACK_NUMBER_TAKEN",
                "Album " + albumId + " already has a track number " + trackNumber);
    }

    private TrackPageDto toPage(Page<Track> tracks) {
        Set<Long> artistIds = tracks.stream().map(Track::getArtistId).collect(Collectors.toSet());
        ArtistProfiles artists = artistIds.isEmpty()
                ? new ArtistProfiles(Map.of(), true)
                : artistDirectory.findProfiles(artistIds);
        return trackMapper.toPage(tracks, artists);
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

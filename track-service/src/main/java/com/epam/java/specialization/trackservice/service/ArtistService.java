package com.epam.java.specialization.trackservice.service;

import com.epam.java.specialization.trackservice.api.dto.ArtistDto;
import com.epam.java.specialization.trackservice.client.ArtistDirectory;
import com.epam.java.specialization.trackservice.client.ArtistProfiles;
import com.epam.java.specialization.trackservice.client.auth.dto.UserSummaryDto;
import com.epam.java.specialization.trackservice.exception.CatalogException;
import com.epam.java.specialization.trackservice.mapper.TrackMapper;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import com.epam.java.specialization.trackservice.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ArtistService {

    private final ArtistDirectory artistDirectory;
    private final TrackRepository trackRepository;

    @Transactional(readOnly = true)
    public ArtistDto getArtist(Long artistId) {
        int trackCount = Math.toIntExact(trackRepository.countByArtistIdAndStatus(artistId, TrackStatus.PUBLISHED));
        ArtistProfiles profiles = artistDirectory.findProfile(artistId);

        if (profiles.available()) {
            UserSummaryDto user = profiles.find(artistId)
                    .filter(u -> profiles.isArtist(artistId))
                    .orElseThrow(() -> artistNotFound(artistId));
            ArtistDto artist = new ArtistDto(artistId, user.getDisplayName(), trackCount, 0, user.getCreatedAt());
            artist.setAvatarUrl(user.getAvatarUrl());
            return artist;
        }

        Instant firstTrack = trackRepository.findFirstCreatedAtByArtistId(artistId)
                .orElseThrow(() -> CatalogException.serviceUnavailable("ARTIST_PROFILE_UNAVAILABLE",
                        "The artist profile cannot be loaded right now. Try again later."));
        return new ArtistDto(artistId, ArtistProfiles.UNKNOWN_ARTIST, trackCount, 0,
                TrackMapper.toOffsetDateTime(firstTrack));
    }

    static CatalogException artistNotFound(Long artistId) {
        return CatalogException.notFound("ARTIST_NOT_FOUND", "Artist " + artistId + " does not exist");
    }
}

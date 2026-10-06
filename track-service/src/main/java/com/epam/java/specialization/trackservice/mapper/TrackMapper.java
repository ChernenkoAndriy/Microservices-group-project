package com.epam.java.specialization.trackservice.mapper;

import com.epam.java.specialization.trackservice.api.dto.ArtistRefDto;
import com.epam.java.specialization.trackservice.api.dto.GenreDto;
import com.epam.java.specialization.trackservice.api.dto.PageMetadataDto;
import com.epam.java.specialization.trackservice.api.dto.TrackDto;
import com.epam.java.specialization.trackservice.api.dto.TrackPageDto;
import com.epam.java.specialization.trackservice.api.dto.TrackStatusDto;
import com.epam.java.specialization.trackservice.client.ArtistProfiles;
import com.epam.java.specialization.trackservice.model.Genre;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class TrackMapper {

    private TrackMapper() {
    }

    public static TrackDto toDto(Track track, ArtistProfiles artists) {
        TrackDto dto = new TrackDto(
                track.getId(),
                track.getTitle(),
                new ArtistRefDto(track.getArtistId(), artists.nameOf(track.getArtistId())),
                track.getGenres().stream().map(TrackMapper::toDto).toList(),
                track.isExplicit(),
                TrackStatusDto.valueOf(track.getStatus().name()),
                toOffsetDateTime(track.getCreatedAt()),
                toOffsetDateTime(track.getUpdatedAt()));
        dto.setTrackNumber(track.getTrackNumber());
        dto.setDurationSeconds(track.getDurationSeconds());
        dto.setCoverUrl(track.getCoverUrl());
        dto.setReleaseDate(track.getReleaseDate());
        dto.setPublishedAt(toOffsetDateTime(track.getPublishedAt()));
        return dto;
    }

    public static TrackPageDto toPage(Page<Track> page, ArtistProfiles artists) {
        return new TrackPageDto(
                page.map(track -> toDto(track, artists)).getContent(),
                new PageMetadataDto(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }

    public static GenreDto toDto(Genre genre) {
        return new GenreDto(genre.getSlug(), genre.getName());
    }

    public static TrackStatus toModel(TrackStatusDto status) {
        return status == null ? null : TrackStatus.valueOf(status.name());
    }

    public static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}

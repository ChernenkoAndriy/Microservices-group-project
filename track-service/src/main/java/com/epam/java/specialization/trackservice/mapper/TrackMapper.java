package com.epam.java.specialization.trackservice.mapper;

import com.epam.java.specialization.trackservice.api.dto.ArtistRefDto;
import com.epam.java.specialization.trackservice.api.dto.GenreDto;
import com.epam.java.specialization.trackservice.api.dto.PageMetadataDto;
import com.epam.java.specialization.trackservice.api.dto.TrackDto;
import com.epam.java.specialization.trackservice.api.dto.TrackPageDto;
import com.epam.java.specialization.trackservice.api.dto.TrackStatusDto;
import com.epam.java.specialization.trackservice.client.ArtistProfiles;
import com.epam.java.specialization.trackservice.internal.api.dto.InternalTrackInfoDto;
import com.epam.java.specialization.trackservice.model.Genre;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

@Mapper(uses = DateTimeMapping.class)
public interface TrackMapper {

    /**
     * @param artists names of the artists, loaded from auth-service for the whole page at once
     */
    @Mapping(target = "artist", expression = "java(toArtistRef(track.getArtistId(), artists))")
    @Mapping(target = "album", ignore = true)
    TrackDto toDto(Track track, @Context ArtistProfiles artists);

    default TrackPageDto toPage(Page<Track> page, ArtistProfiles artists) {
        return new TrackPageDto(page.map(track -> toDto(track, artists)).getContent(), toPageMetadata(page));
    }

    PageMetadataDto toPageMetadata(Page<?> page);

    GenreDto toDto(Genre genre);

    InternalTrackInfoDto toInternalInfo(Track track);

    TrackStatus toModel(TrackStatusDto status);

    default ArtistRefDto toArtistRef(Long artistId, ArtistProfiles artists) {
        return new ArtistRefDto(artistId, artists.nameOf(artistId));
    }
}

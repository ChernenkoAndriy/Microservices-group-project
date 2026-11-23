package com.epam.java.specialization.libraryservice.client;

import com.epam.java.specialization.libraryservice.client.auth.api.InternalUsersApi;
import com.epam.java.specialization.libraryservice.client.auth.dto.UserRoleDto;
import com.epam.java.specialization.libraryservice.client.auth.dto.UserSummaryDto;
import com.epam.java.specialization.libraryservice.client.catalog.api.InternalTracksApi;
import com.epam.java.specialization.libraryservice.client.catalog.dto.InternalTrackInfoDto;
import com.epam.java.specialization.libraryservice.client.catalog.dto.TrackStatusDto;
import com.epam.java.specialization.libraryservice.exception.LibraryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Set;

/**
 * Synchronous checks before a like or a follow (api-contracts/README.md, "Calls between services"):
 * the track must exist and be published, the artist must exist and have the ARTIST role.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogDirectory {

    private final InternalTracksApi tracksApi;
    private final InternalUsersApi usersApi;

    /** @throws LibraryException 404 {@code TRACK_NOT_FOUND}, or 503 when catalog-service does not answer */
    public void requirePublishedTrack(Long trackId) {
        InternalTrackInfoDto track;
        try {
            track = tracksApi.getTrackInfo(trackId).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw trackNotFound(trackId);
        } catch (RestClientException e) {
            log.warn("Could not check track id={} in catalog-service: {}", trackId, e.toString());
            throw LibraryException.serviceUnavailable("CATALOG_UNAVAILABLE",
                    "The track cannot be checked right now. Try again later.");
        }
        if (track == null || track.getStatus() != TrackStatusDto.PUBLISHED) {
            throw trackNotFound(trackId);
        }
    }

    /** @throws LibraryException 404 {@code ARTIST_NOT_FOUND}, or 503 when auth-service does not answer */
    public void requireArtist(Long artistId) {
        List<UserSummaryDto> users;
        try {
            users = usersApi.getUsersByIds(Set.of(artistId)).getBody();
        } catch (RestClientException e) {
            log.warn("Could not check artist id={} in auth-service: {}", artistId, e.toString());
            throw LibraryException.serviceUnavailable("AUTH_UNAVAILABLE",
                    "The artist cannot be checked right now. Try again later.");
        }
        boolean isArtist = users != null && users.stream()
                .anyMatch(user -> artistId.equals(user.getId()) && user.getRole() == UserRoleDto.ARTIST);
        if (!isArtist) {
            throw LibraryException.notFound("ARTIST_NOT_FOUND", "Artist " + artistId + " does not exist");
        }
    }

    private static LibraryException trackNotFound(Long trackId) {
        return LibraryException.notFound("TRACK_NOT_FOUND", "No published track with id " + trackId);
    }
}

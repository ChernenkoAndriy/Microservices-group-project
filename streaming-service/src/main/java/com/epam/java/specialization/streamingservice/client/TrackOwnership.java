package com.epam.java.specialization.streamingservice.client;

import com.epam.java.specialization.streamingservice.client.catalog.api.InternalTracksApi;
import com.epam.java.specialization.streamingservice.client.catalog.dto.InternalTrackInfoDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/** Asks catalog-service whether a track exists and belongs to the caller, before its audio is accepted. */
@Component
public class TrackOwnership {

    private static final Logger log = LoggerFactory.getLogger(TrackOwnership.class);

    private final InternalTracksApi catalog;

    public TrackOwnership(InternalTracksApi catalog) {
        this.catalog = catalog;
    }

    public void requireOwner(Long trackId, Long userId) {
        InternalTrackInfoDto track;
        try {
            track = catalog.getTrackInfo(trackId).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Track " + trackId + " does not exist");
        } catch (RestClientException e) {
            log.warn("Could not check the owner of track id={} in catalog-service", trackId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not check the track in catalog-service", e);
        }
        if (track == null || !Objects.equals(track.getArtistId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the artist of track " + trackId
                    + " can upload its audio");
        }
    }
}

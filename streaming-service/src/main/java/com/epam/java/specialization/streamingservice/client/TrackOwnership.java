package com.epam.java.specialization.streamingservice.client;

import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Asks catalog-service (gRPC GetTrackInfo) whether a track exists and belongs to the caller, before its audio is accepted. */
@Component
public class TrackOwnership {

    private static final Logger log = LoggerFactory.getLogger(TrackOwnership.class);

    private final CatalogGrpcClient catalog;

    public TrackOwnership(CatalogGrpcClient catalog) {
        this.catalog = catalog;
    }

    public void requireOwner(Long trackId, Long userId) {
        TrackInfo track;
        try {
            track = catalog.getTrackInfo(trackId);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Track " + trackId + " does not exist");
            }
            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getStatus().getDescription());
            }
            // UNAVAILABLE (after the retries), DEADLINE_EXCEEDED and anything else: catalog-service could not answer.
            log.warn("Could not check the owner of track id={} in catalog-service", trackId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not check the track in catalog-service", e);
        }
        if (track.getArtistId() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the artist of track " + trackId
                    + " can upload its audio");
        }
    }
}

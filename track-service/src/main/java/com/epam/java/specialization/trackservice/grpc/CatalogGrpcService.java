package com.epam.java.specialization.trackservice.grpc;

import com.epam.java.specialization.grpc.catalog.v1.CatalogInternalServiceGrpc;
import com.epam.java.specialization.grpc.catalog.v1.GetTrackInfoRequest;
import com.epam.java.specialization.grpc.catalog.v1.ListArtistTracksRequest;
import com.epam.java.specialization.grpc.catalog.v1.PlayEvent;
import com.epam.java.specialization.grpc.catalog.v1.PlaySummary;
import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import com.epam.java.specialization.grpc.catalog.v1.TrackPlayCount;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatusUpdate;
import com.epam.java.specialization.grpc.catalog.v1.WatchTrackStatusRequest;
import com.epam.java.specialization.trackservice.exception.CatalogException;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.service.TrackService;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * gRPC server side of catalog-service (api-contracts/catalog-service/grpc/catalog_internal.proto), port 9090.
 * <p>
 * Errors are thrown as {@link CatalogException}, like in the REST controllers; {@link CatalogGrpcExceptionHandler}
 * turns them into gRPC status codes (404 -> NOT_FOUND, 400 -> INVALID_ARGUMENT). gRPC calls the methods of one
 * call's request observer one at a time, so the observers below need no synchronization.
 */
@Slf4j
@GrpcService(interceptors = InternalTokenServerInterceptor.class)
@RequiredArgsConstructor
public class CatalogGrpcService extends CatalogInternalServiceGrpc.CatalogInternalServiceImplBase {

    private final TrackService trackService;
    private final GrpcTrackMapper mapper;

    /** Unary: one request, one response. */
    @Override
    public void getTrackInfo(GetTrackInfoRequest request, StreamObserver<TrackInfo> responseObserver) {
        Track track = trackService.getTrack(requirePositive(request.getTrackId(), "track_id"));
        responseObserver.onNext(mapper.toTrackInfo(track));
        responseObserver.onCompleted();
    }

    /** Server streaming: one request, one message per track, then completed. */
    @Override
    public void listArtistTracks(ListArtistTracksRequest request, StreamObserver<TrackInfo> responseObserver) {
        long artistId = requirePositive(request.getArtistId(), "artist_id");
        List<Track> tracks = trackService.listAllArtistTracks(artistId);
        int sent = 0;
        for (Track track : tracks) {
            // The client went away or its deadline expired: stop producing messages nobody reads.
            if (Context.current().isCancelled()) {
                log.info("ListArtistTracks for artist id={} cancelled after {} of {} tracks", artistId, sent, tracks.size());
                return;
            }
            responseObserver.onNext(mapper.toTrackInfo(track));
            sent++;
        }
        responseObserver.onCompleted();
    }

    /**
     * Client streaming: the client sends play events and then completes; only then the plays are saved
     * (one transaction) and the single summary is sent back.
     */
    @Override
    public StreamObserver<PlayEvent> reportPlays(StreamObserver<PlaySummary> responseObserver) {
        return new StreamObserver<>() {
            private final Map<Long, Integer> playsByTrack = new LinkedHashMap<>();
            private int eventsReceived;

            @Override
            public void onNext(PlayEvent event) {
                requirePositive(event.getTrackId(), "track_id");
                if (event.getListenedSeconds() < 0) {
                    throw CatalogException.badRequest("INVALID_LISTENED_SECONDS", "listened_seconds must not be negative");
                }
                eventsReceived++;
                playsByTrack.merge(event.getTrackId(), 1, Integer::sum);
            }

            @Override
            public void onError(Throwable t) {
                // The client cancelled or failed: nothing has been saved, drop what was collected.
                log.warn("ReportPlays aborted by the client after {} events: {}", eventsReceived, Status.fromThrowable(t));
                playsByTrack.clear();
            }

            @Override
            public void onCompleted() {
                if (Context.current().isCancelled()) {
                    log.info("ReportPlays cancelled before the {} events were saved", eventsReceived);
                    return;
                }
                Map<Long, Long> totals = trackService.addPlays(playsByTrack);
                PlaySummary.Builder summary = PlaySummary.newBuilder().setEventsReceived(eventsReceived);
                totals.forEach((trackId, total) -> summary.addTracks(TrackPlayCount.newBuilder()
                        .setTrackId(trackId)
                        .setPlaysAdded(playsByTrack.get(trackId))
                        .setTotalPlays(total)));
                log.info("ReportPlays saved {} events for {} tracks", eventsReceived, totals.size());
                responseObserver.onNext(summary.build());
                responseObserver.onCompleted();
            }
        };
    }

    /**
     * Bidirectional streaming: both sides send whenever they want over one call. Here every request is answered
     * with the current status of that track; the call ends when the client completes it.
     */
    @Override
    public StreamObserver<WatchTrackStatusRequest> watchTrackStatus(StreamObserver<TrackStatusUpdate> responseObserver) {
        return new StreamObserver<>() {
            private int updatesSent;

            @Override
            public void onNext(WatchTrackStatusRequest request) {
                if (Context.current().isCancelled()) {
                    return;
                }
                Track track = trackService.getTrack(requirePositive(request.getTrackId(), "track_id"));
                responseObserver.onNext(mapper.toStatusUpdate(track));
                updatesSent++;
            }

            @Override
            public void onError(Throwable t) {
                log.info("WatchTrackStatus ended by the client after {} updates: {}", updatesSent, Status.fromThrowable(t));
            }

            @Override
            public void onCompleted() {
                log.debug("WatchTrackStatus completed after {} updates", updatesSent);
                responseObserver.onCompleted();
            }
        };
    }

    /** Proto3 has no "required": a missing int64 arrives as 0. */
    private static long requirePositive(long value, String field) {
        if (value <= 0) {
            throw CatalogException.badRequest("INVALID_ARGUMENT", field + " must be a positive id");
        }
        return value;
    }
}

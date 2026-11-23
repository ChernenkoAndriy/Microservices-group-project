package com.epam.java.specialization.streamingservice.client;

import com.epam.java.specialization.grpc.catalog.v1.CatalogInternalServiceGrpc.CatalogInternalServiceBlockingStub;
import com.epam.java.specialization.grpc.catalog.v1.CatalogInternalServiceGrpc.CatalogInternalServiceStub;
import com.epam.java.specialization.grpc.catalog.v1.GetTrackInfoRequest;
import com.epam.java.specialization.grpc.catalog.v1.ListArtistTracksRequest;
import com.epam.java.specialization.grpc.catalog.v1.PlayEvent;
import com.epam.java.specialization.grpc.catalog.v1.PlaySummary;
import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatusUpdate;
import com.epam.java.specialization.grpc.catalog.v1.WatchTrackStatusRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * The four calls of catalog-service's gRPC API, one per kind of gRPC call. Every method blocks until the call ends
 * and throws {@link StatusRuntimeException} when it fails; the channel's default deadline bounds the wait.
 */
@Component
public class CatalogGrpcClient {

    private final CatalogInternalServiceBlockingStub blockingStub;
    private final CatalogInternalServiceStub asyncStub;

    public CatalogGrpcClient(CatalogInternalServiceBlockingStub blockingStub, CatalogInternalServiceStub asyncStub) {
        this.blockingStub = blockingStub;
        this.asyncStub = asyncStub;
    }

    /** Unary. Retried on UNAVAILABLE (application.yaml). */
    public TrackInfo getTrackInfo(long trackId) {
        return blockingStub.getTrackInfo(GetTrackInfoRequest.newBuilder().setTrackId(trackId).build());
    }

    /** Server streaming: the blocking stub returns an iterator over the streamed tracks. */
    public List<TrackInfo> listArtistTracks(long artistId) {
        List<TrackInfo> tracks = new ArrayList<>();
        blockingStub.listArtistTracks(ListArtistTracksRequest.newBuilder().setArtistId(artistId).build())
                .forEachRemaining(tracks::add);
        return tracks;
    }

    /**
     * Client streaming: sends the events one by one, then completes the request stream; the server answers once,
     * with the summary. Not retried: a repeated call would count the plays twice.
     */
    public PlaySummary reportPlays(Collection<PlayEvent> events) {
        CompletableFuture<PlaySummary> summary = new CompletableFuture<>();
        StreamObserver<PlayEvent> requests = asyncStub.reportPlays(new StreamObserver<>() {
            private PlaySummary received;

            @Override
            public void onNext(PlaySummary value) {
                received = value;
            }

            @Override
            public void onError(Throwable t) {
                summary.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                if (received == null) {
                    // Completed with OK but without the single response a client-streaming call must return.
                    summary.completeExceptionally(Status.INTERNAL
                            .withDescription("ReportPlays completed without a summary").asRuntimeException());
                } else {
                    summary.complete(received);
                }
            }
        });
        events.forEach(requests::onNext);
        requests.onCompleted();
        return await(summary);
    }

    /**
     * Bidirectional streaming: sends one request per track over a single call and collects the status updates the
     * server sends back, until the server completes the call.
     */
    public List<TrackStatusUpdate> watchTrackStatus(Collection<Long> trackIds) {
        CompletableFuture<List<TrackStatusUpdate>> result = new CompletableFuture<>();
        StreamObserver<WatchTrackStatusRequest> requests = asyncStub.watchTrackStatus(new StreamObserver<>() {
            // gRPC delivers the responses of one call one at a time, so a plain list is enough.
            private final List<TrackStatusUpdate> updates = new ArrayList<>();

            @Override
            public void onNext(TrackStatusUpdate update) {
                updates.add(update);
            }

            @Override
            public void onError(Throwable t) {
                result.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                result.complete(updates);
            }
        });
        trackIds.forEach(id -> requests.onNext(WatchTrackStatusRequest.newBuilder().setTrackId(id).build()));
        requests.onCompleted();
        return await(result);
    }

    private static <T> T await(CompletableFuture<T> result) {
        try {
            return result.get();
        } catch (ExecutionException e) {
            throw Status.fromThrowable(e.getCause()).asRuntimeException();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Status.CANCELLED.withDescription("Interrupted while waiting for catalog-service").asRuntimeException();
        }
    }
}

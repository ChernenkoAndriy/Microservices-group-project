package com.epam.java.specialization.streamingservice.client;

import com.epam.java.specialization.grpc.catalog.v1.CatalogInternalServiceGrpc;
import com.epam.java.specialization.grpc.catalog.v1.GetTrackInfoRequest;
import com.epam.java.specialization.grpc.catalog.v1.ListArtistTracksRequest;
import com.epam.java.specialization.grpc.catalog.v1.PlayEvent;
import com.epam.java.specialization.grpc.catalog.v1.PlaySummary;
import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import com.epam.java.specialization.grpc.catalog.v1.TrackPlayCount;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatus;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatusUpdate;
import com.epam.java.specialization.grpc.catalog.v1.WatchTrackStatusRequest;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A catalog-service stand-in on a free localhost port. It records every attempt of every call (with its metadata)
 * and can be told to fail the next attempts of a method with a given status, or to never answer.
 */
final class FakeCatalogService extends CatalogInternalServiceGrpc.CatalogInternalServiceImplBase {

    private static final Metadata.Key<String> TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> PREVIOUS_ATTEMPTS =
            Metadata.Key.of("grpc-previous-rpc-attempts", Metadata.ASCII_STRING_MARSHALLER);

    private final Server server;
    private final Map<Long, TrackInfo> tracks = new ConcurrentHashMap<>();
    /** Bare method name (GetTrackInfo, ...) -> metadata of every attempt, in order. */
    private final Map<String, List<Metadata>> attempts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failuresLeft = new ConcurrentHashMap<>();
    private final Map<String, Status> failureStatus = new ConcurrentHashMap<>();
    private final Set<String> hanging = ConcurrentHashMap.newKeySet();
    private final List<PlayEvent> reportedPlays = new CopyOnWriteArrayList<>();

    private FakeCatalogService() {
        try {
            this.server = Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                    .addService(ServerInterceptors.intercept(this, new AttemptRecorder()))
                    .build()
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static FakeCatalogService start() {
        return new FakeCatalogService();
    }

    String target() {
        return "localhost:" + server.getPort();
    }

    void stop() {
        server.shutdownNow();
    }

    void reset() {
        tracks.clear();
        attempts.clear();
        failuresLeft.clear();
        failureStatus.clear();
        hanging.clear();
        reportedPlays.clear();
    }

    void addTrack(long id, long artistId, TrackStatus status) {
        tracks.put(id, TrackInfo.newBuilder().setId(id).setArtistId(artistId).setStatus(status)
                .setTitle("Track " + id).build());
    }

    /** The next {@code count} attempts of the method fail with {@code status}. */
    void failNext(String method, int count, Status status) {
        failuresLeft.put(method, new AtomicInteger(count));
        failureStatus.put(method, status);
    }

    /** Calls of the method are never answered, so the client's deadline ends them. */
    void hangNext(String method) {
        hanging.add(method);
    }

    int attempts(String method) {
        return attempts.getOrDefault(method, List.of()).size();
    }

    List<String> previousAttemptsHeaders(String method) {
        List<String> values = new ArrayList<>();
        attempts.getOrDefault(method, List.of()).forEach(headers -> values.add(headers.get(PREVIOUS_ATTEMPTS)));
        return values;
    }

    Set<String> tokens() {
        Set<String> tokens = ConcurrentHashMap.newKeySet();
        attempts.values().forEach(list -> list.forEach(headers -> {
            String token = headers.get(TOKEN);
            tokens.add(token != null ? token : "<none>");
        }));
        return tokens;
    }

    List<PlayEvent> reportedPlays() {
        return List.copyOf(reportedPlays);
    }

    // ---- the service ----

    @Override
    public void getTrackInfo(GetTrackInfoRequest request, StreamObserver<TrackInfo> responseObserver) {
        if (hanging.contains("GetTrackInfo") || failed("GetTrackInfo", responseObserver)) {
            return;
        }
        TrackInfo track = tracks.get(request.getTrackId());
        if (track == null) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Track not found").asRuntimeException());
            return;
        }
        responseObserver.onNext(track);
        responseObserver.onCompleted();
    }

    @Override
    public void listArtistTracks(ListArtistTracksRequest request, StreamObserver<TrackInfo> responseObserver) {
        if (failed("ListArtistTracks", responseObserver)) {
            return;
        }
        tracks.values().stream()
                .filter(track -> track.getArtistId() == request.getArtistId())
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .forEach(responseObserver::onNext);
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<PlayEvent> reportPlays(StreamObserver<PlaySummary> responseObserver) {
        List<PlayEvent> received = new ArrayList<>();
        return new StreamObserver<>() {
            @Override
            public void onNext(PlayEvent event) {
                received.add(event);
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
                // Fails after the events arrived, like a server that went away mid-call.
                if (failed("ReportPlays", responseObserver)) {
                    return;
                }
                reportedPlays.addAll(received);
                Map<Long, Integer> playsByTrack = new LinkedHashMap<>();
                received.forEach(event -> playsByTrack.merge(event.getTrackId(), 1, Integer::sum));
                PlaySummary.Builder summary = PlaySummary.newBuilder().setEventsReceived(received.size());
                playsByTrack.forEach((trackId, plays) -> summary.addTracks(TrackPlayCount.newBuilder()
                        .setTrackId(trackId).setPlaysAdded(plays).setTotalPlays(plays)));
                responseObserver.onNext(summary.build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<WatchTrackStatusRequest> watchTrackStatus(StreamObserver<TrackStatusUpdate> responseObserver) {
        boolean failed = failed("WatchTrackStatus", responseObserver);
        return new StreamObserver<>() {
            private boolean closed = failed;

            @Override
            public void onNext(WatchTrackStatusRequest request) {
                if (closed) {
                    return;
                }
                TrackInfo track = tracks.get(request.getTrackId());
                if (track == null) {
                    closed = true;
                    responseObserver.onError(Status.NOT_FOUND.withDescription("Track not found").asRuntimeException());
                    return;
                }
                responseObserver.onNext(TrackStatusUpdate.newBuilder().setTrackId(track.getId())
                        .setStatus(track.getStatus()).build());
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
                if (!closed) {
                    responseObserver.onCompleted();
                }
            }
        };
    }

    private boolean failed(String method, StreamObserver<?> responseObserver) {
        AtomicInteger left = failuresLeft.get(method);
        if (left != null && left.getAndDecrement() > 0) {
            responseObserver.onError(failureStatus.get(method).withDescription("Injected failure").asRuntimeException());
            return true;
        }
        return false;
    }

    /** Records the metadata of every attempt, per method. */
    private final class AttemptRecorder implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            String method = call.getMethodDescriptor().getBareMethodName();
            attempts.computeIfAbsent(method, m -> Collections.synchronizedList(new ArrayList<>())).add(headers);
            return next.startCall(call, headers);
        }
    }
}

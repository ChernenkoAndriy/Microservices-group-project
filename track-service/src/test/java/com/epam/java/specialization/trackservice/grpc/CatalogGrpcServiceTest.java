package com.epam.java.specialization.trackservice.grpc;

import com.epam.java.specialization.grpc.catalog.v1.CatalogInternalServiceGrpc;
import com.epam.java.specialization.grpc.catalog.v1.GetTrackInfoRequest;
import com.epam.java.specialization.grpc.catalog.v1.ListArtistTracksRequest;
import com.epam.java.specialization.grpc.catalog.v1.PlayEvent;
import com.epam.java.specialization.grpc.catalog.v1.PlaySummary;
import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatusUpdate;
import com.epam.java.specialization.grpc.catalog.v1.WatchTrackStatusRequest;
import com.epam.java.specialization.trackservice.exception.CatalogException;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import com.epam.java.specialization.trackservice.service.TrackService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:track-grpc-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "internal-api.token=test-token",
})
class CatalogGrpcServiceTest {

    @Autowired
    private GrpcServerLifecycle grpcServer;

    @MockitoBean
    private TrackService trackService;

    private ManagedChannel channel;
    private CatalogInternalServiceGrpc.CatalogInternalServiceBlockingStub blockingStub;
    private CatalogInternalServiceGrpc.CatalogInternalServiceStub asyncStub;

    @BeforeEach
    void setUp() {
        channel = ManagedChannelBuilder.forAddress("localhost", grpcServer.getPort())
                .usePlaintext()
                .build();
        Metadata headers = new Metadata();
        headers.put(InternalTokenServerInterceptor.INTERNAL_TOKEN, "test-token");
        var authInterceptor = MetadataUtils.newAttachHeadersInterceptor(headers);
        blockingStub = CatalogInternalServiceGrpc.newBlockingStub(channel).withInterceptors(authInterceptor);
        asyncStub = CatalogInternalServiceGrpc.newStub(channel).withInterceptors(authInterceptor);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void getTrackInfoReturnsTrack() {
        when(trackService.getTrack(1L)).thenReturn(track(1L, 7L, TrackStatus.PUBLISHED, "Test Track", 180));

        TrackInfo info = blockingStub.getTrackInfo(GetTrackInfoRequest.newBuilder().setTrackId(1L).build());

        assertThat(info.getId()).isEqualTo(1L);
        assertThat(info.getArtistId()).isEqualTo(7L);
        assertThat(info.getTitle()).isEqualTo("Test Track");
        assertThat(info.getDurationSeconds()).isEqualTo(180);
    }

    @Test
    void listArtistTracksStreamsAllTracks() {
        when(trackService.listAllArtistTracks(7L)).thenReturn(List.of(
                track(1L, 7L, TrackStatus.PUBLISHED, "First", 120),
                track(2L, 7L, TrackStatus.AWAITING_AUDIO, "Second", null)
        ));

        List<TrackInfo> result = new ArrayList<>();
        blockingStub.listArtistTracks(ListArtistTracksRequest.newBuilder().setArtistId(7L).build())
                .forEachRemaining(result::add);

        assertThat(result).extracting(TrackInfo::getTitle).containsExactly("First", "Second");
        assertThat(result.get(1).hasDurationSeconds()).isFalse();
    }

    @Test
    void reportPlaysAggregatesClientStream() throws Exception {
        when(trackService.addPlays(anyMap())).thenReturn(Map.of(1L, 12L, 2L, 5L));

        CompletableFuture<PlaySummary> future = new CompletableFuture<>();
        StreamObserver<PlayEvent> requests = asyncStub.reportPlays(new StreamObserver<>() {
            private PlaySummary summary;

            @Override
            public void onNext(PlaySummary value) {
                summary = value;
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                future.complete(summary);
            }
        });

        requests.onNext(PlayEvent.newBuilder().setTrackId(1L).setUserId(2L).setListenedSeconds(45).build());
        requests.onNext(PlayEvent.newBuilder().setTrackId(1L).setUserId(3L).setListenedSeconds(30).build());
        requests.onNext(PlayEvent.newBuilder().setTrackId(2L).setUserId(2L).setListenedSeconds(15).build());
        requests.onCompleted();

        PlaySummary summary = future.get(5, TimeUnit.SECONDS);
        assertThat(summary.getEventsReceived()).isEqualTo(3);
        assertThat(summary.getTracksCount()).isEqualTo(2);
    }

    @Test
    void watchTrackStatusStreamsUpdatesBidirectionally() throws Exception {
        when(trackService.getTrack(1L)).thenReturn(track(1L, 7L, TrackStatus.PUBLISHED, "First", 180));
        when(trackService.getTrack(2L)).thenReturn(track(2L, 7L, TrackStatus.AWAITING_AUDIO, "Second", null));

        List<TrackStatusUpdate> updates = new ArrayList<>();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        StreamObserver<WatchTrackStatusRequest> requests = asyncStub.watchTrackStatus(new StreamObserver<>() {
            @Override
            public void onNext(TrackStatusUpdate value) {
                updates.add(value);
            }

            @Override
            public void onError(Throwable t) {
                completed.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                completed.complete(null);
            }
        });

        requests.onNext(WatchTrackStatusRequest.newBuilder().setTrackId(1L).build());
        requests.onNext(WatchTrackStatusRequest.newBuilder().setTrackId(2L).build());
        requests.onCompleted();

        completed.get(5, TimeUnit.SECONDS);
        assertThat(updates).extracting(TrackStatusUpdate::getTrackId).containsExactly(1L, 2L);
    }

    @Test
    void rejectsCallWithoutInternalToken() {
        var unauthenticatedStub = CatalogInternalServiceGrpc.newBlockingStub(channel);

        assertThatThrownBy(() -> unauthenticatedStub.getTrackInfo(GetTrackInfoRequest.newBuilder().setTrackId(1L).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void mapsMissingTrackToNotFound() {
        when(trackService.getTrack(999L)).thenThrow(CatalogException.notFound("TRACK_NOT_FOUND", "Track 999 does not exist"));

        assertThatThrownBy(() -> blockingStub.getTrackInfo(GetTrackInfoRequest.newBuilder().setTrackId(999L).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    void mapsNonPositiveIdToInvalidArgument() {
        assertThatThrownBy(() -> blockingStub.getTrackInfo(GetTrackInfoRequest.newBuilder().setTrackId(0L).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    void mapsDatabaseFailureToUnavailable() {
        when(trackService.getTrack(1L)).thenThrow(new CannotCreateTransactionException("DB down"));

        assertThatThrownBy(() -> blockingStub.getTrackInfo(GetTrackInfoRequest.newBuilder().setTrackId(1L).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
    }

    private static Track track(long id, long artistId, TrackStatus status, String title, Integer durationSeconds) {
        Track track = new Track();
        track.setId(id);
        track.setArtistId(artistId);
        track.setStatus(status);
        track.setTitle(title);
        track.setDurationSeconds(durationSeconds);
        track.setUpdatedAt(Instant.now());
        return track;
    }
}

package com.epam.java.specialization.streamingservice.client;

import com.epam.java.specialization.grpc.catalog.v1.CatalogInternalServiceGrpc;
import com.epam.java.specialization.grpc.catalog.v1.GetTrackInfoRequest;
import com.epam.java.specialization.grpc.catalog.v1.PlayEvent;
import com.epam.java.specialization.grpc.catalog.v1.PlaySummary;
import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatus;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streaming-grpc-retry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "catalog-grpc.internal-token=test-token",
})
class CatalogGrpcRetryTest {

    private static final AtomicInteger getTrackAttempts = new AtomicInteger();
    private static final AtomicInteger reportPlaysAttempts = new AtomicInteger();
    private static final AtomicBoolean failAllGetTrack = new AtomicBoolean();
    private static final Server server = startServer();

    @DynamicPropertySource
    static void target(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.client.channel.catalog.target", () -> "localhost:" + server.getPort());
    }

    @AfterAll
    static void stopServer() {
        server.shutdownNow();
    }

    @Autowired
    private CatalogGrpcClient client;

    @BeforeEach
    void reset() {
        getTrackAttempts.set(0);
        reportPlaysAttempts.set(0);
        failAllGetTrack.set(false);
    }

    @Test
    void getTrackInfoRetriesOnUnavailableAndSucceedsOnThirdAttempt() {
        TrackInfo track = client.getTrackInfo(1L);

        assertThat(track.getId()).isEqualTo(1L);
        assertThat(track.getTitle()).isEqualTo("Recovered Track");
        assertThat(getTrackAttempts.get()).isEqualTo(3);
    }

    @Test
    void getTrackInfoStopsAfterThreeAttemptsWhenUnavailablePersists() {
        failAllGetTrack.set(true);

        assertThatThrownBy(() -> client.getTrackInfo(1L))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
        assertThat(getTrackAttempts.get()).isEqualTo(3);
    }

    @Test
    void reportPlaysDoesNotRetryOnUnavailable() {
        List<PlayEvent> events = List.of(PlayEvent.newBuilder().setTrackId(1L).setUserId(2L).setListenedSeconds(30).build());

        assertThatThrownBy(() -> client.reportPlays(events))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
        assertThat(reportPlaysAttempts.get()).isEqualTo(1);
    }

    private static Server startServer() {
        try {
            return Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                    .addService(new CatalogInternalServiceGrpc.CatalogInternalServiceImplBase() {
                        @Override
                        public void getTrackInfo(GetTrackInfoRequest request, StreamObserver<TrackInfo> responseObserver) {
                            int attempt = getTrackAttempts.incrementAndGet();
                            if (failAllGetTrack.get() || attempt < 3) {
                                responseObserver.onError(Status.UNAVAILABLE
                                        .withDescription("Simulated transient failure").asRuntimeException());
                                return;
                            }
                            responseObserver.onNext(TrackInfo.newBuilder()
                                    .setId(request.getTrackId())
                                    .setArtistId(7L)
                                    .setStatus(TrackStatus.TRACK_STATUS_PUBLISHED)
                                    .setTitle("Recovered Track")
                                    .build());
                            responseObserver.onCompleted();
                        }

                        @Override
                        public StreamObserver<PlayEvent> reportPlays(StreamObserver<PlaySummary> responseObserver) {
                            reportPlaysAttempts.incrementAndGet();
                            return new StreamObserver<>() {
                                @Override
                                public void onNext(PlayEvent value) {
                                }

                                @Override
                                public void onError(Throwable t) {
                                }

                                @Override
                                public void onCompleted() {
                                    responseObserver.onError(Status.UNAVAILABLE
                                            .withDescription("Simulated failure").asRuntimeException());
                                }
                            };
                        }
                    })
                    .build()
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

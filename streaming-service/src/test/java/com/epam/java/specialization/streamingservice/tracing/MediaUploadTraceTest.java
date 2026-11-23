package com.epam.java.specialization.streamingservice.tracing;

import com.epam.java.specialization.grpc.catalog.v1.CatalogInternalServiceGrpc;
import com.epam.java.specialization.grpc.catalog.v1.GetTrackInfoRequest;
import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatus;
import com.epam.java.specialization.streamingservice.outbox.OutboxEventRepository;
import com.epam.java.specialization.streamingservice.repository.TranscodingJobRepository;
import com.epam.java.specialization.streamingservice.service.StreamingStorageService;
import io.grpc.ForwardingServerCallListener;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An upload checks the track's owner in catalog-service over gRPC (GetTrackInfo). The call carries the upload's
 * trace ({@code traceparent} metadata), and the queued job keeps it for the worker.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streaming-upload;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "catalog-grpc.internal-token=test-token",
})
@AutoConfigureMockMvc
@AutoConfigureTracing
class MediaUploadTraceTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
    private static final long ARTIST_ID = 7L;

    private static final Metadata.Key<String> TRACEPARENT_KEY =
            Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> INTERNAL_TOKEN_KEY =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    /** Artist id per track id known to the fake catalog-service. */
    static final Map<Long, Long> tracks = new ConcurrentHashMap<>();
    /** Every GetTrackInfo call received: the requested track id and the call's metadata. */
    static final List<Map.Entry<Long, Metadata>> calls = new CopyOnWriteArrayList<>();
    static final Server catalogService = startCatalogService();

    @DynamicPropertySource
    static void catalogServiceTarget(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.client.channel.catalog.target", () -> "localhost:" + catalogService.getPort());
    }

    @AfterAll
    static void stopCatalogService() {
        catalogService.shutdownNow();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TranscodingJobRepository jobRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @MockitoBean
    private StreamingStorageService storageService;

    @BeforeEach
    void cleanUp() {
        tracks.clear();
        calls.clear();
        outboxRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void uploadContinuesTheIncomingTraceInCatalogServiceAndInTheJob() throws Exception {
        stubTrack(5, ARTIST_ID);

        mockMvc.perform(upload(5).header("traceparent", TRACEPARENT).header("X-User-Id", ARTIST_ID))
                .andExpect(status().isAccepted());

        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().getKey()).isEqualTo(5L);
        Metadata headers = calls.getFirst().getValue();
        assertThat(headers.get(TRACEPARENT_KEY)).contains(TRACE_ID);
        assertThat(headers.get(INTERNAL_TOKEN_KEY)).isEqualTo("test-token");
        String jobTraceparent = jobRepository.findByTrackId(5L).orElseThrow().getTraceparent();
        assertThat(jobTraceparent).startsWith("00-" + TRACE_ID + "-").isNotEqualTo(TRACEPARENT);
    }

    @Test
    void anotherArtistsTrackIsForbidden() throws Exception {
        stubTrack(6, 99L);

        mockMvc.perform(upload(6).header("X-User-Id", ARTIST_ID)).andExpect(status().isForbidden());

        assertThat(jobRepository.findByTrackId(6L)).isEmpty();
    }

    @Test
    void unknownTrackIsNotFound() throws Exception {
        // Track 8 is unknown to the fake catalog-service, which answers NOT_FOUND.
        mockMvc.perform(upload(8).header("X-User-Id", ARTIST_ID)).andExpect(status().isNotFound());
    }

    @Test
    void uploadWithoutAUserIsUnauthorized() throws Exception {
        mockMvc.perform(upload(5)).andExpect(status().isUnauthorized());

        assertThat(calls).isEmpty();
    }

    private static MockMultipartHttpServletRequestBuilder upload(long trackId) {
        MockMultipartFile file = new MockMultipartFile("file", "master.mp3", "audio/mpeg", new byte[1024]);
        return multipart("/api/v1/media/{trackId}/master", trackId).file(file);
    }

    private static void stubTrack(long trackId, long artistId) {
        tracks.put(trackId, artistId);
    }

    /** A fake catalog-service: GetTrackInfo answers from {@link #tracks} and records every call. */
    private static Server startCatalogService() {
        var service = new CatalogInternalServiceGrpc.CatalogInternalServiceImplBase() {
            @Override
            public void getTrackInfo(GetTrackInfoRequest request, StreamObserver<TrackInfo> responseObserver) {
                Long artistId = tracks.get(request.getTrackId());
                if (artistId == null) {
                    responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
                    return;
                }
                responseObserver.onNext(TrackInfo.newBuilder().setId(request.getTrackId()).setArtistId(artistId)
                        .setStatus(TrackStatus.TRACK_STATUS_AWAITING_AUDIO).build());
                responseObserver.onCompleted();
            }
        };
        ServerInterceptor recordCalls = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                         Metadata headers,
                                                                         ServerCallHandler<ReqT, RespT> next) {
                return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(
                        next.startCall(call, headers)) {
                    @Override
                    public void onMessage(ReqT message) {
                        if (message instanceof GetTrackInfoRequest request) {
                            calls.add(Map.entry(request.getTrackId(), headers));
                        }
                        super.onMessage(message);
                    }
                };
            }
        };
        try {
            return Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                    .addService(ServerInterceptors.intercept(service, recordCalls))
                    .build()
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

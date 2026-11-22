package com.epam.java.specialization.streamingservice.controller;

import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatus;
import com.epam.java.specialization.streamingservice.client.CatalogGrpcClient;
import com.epam.java.specialization.streamingservice.service.PlayReporter;
import com.epam.java.specialization.streamingservice.service.StreamTokenService;
import com.epam.java.specialization.streamingservice.service.StreamingStorageService;
import com.epam.java.specialization.streamingservice.web.CurrentUser;
import com.epam.java.specialization.streamingservice.web.StreamingExceptionHandler;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StreamingControllerTest {

    private static final byte[] AUDIO = "0123456789".getBytes(StandardCharsets.US_ASCII);
    private static final String KEY = StreamingStorageService.masterKey(7L);

    @Mock
    private StreamingStorageService storage;
    @Mock
    private PlayReporter playReporter;
    @Mock
    private CatalogGrpcClient catalog;

    private final StreamTokenService tokens = new StreamTokenService("controller-test-secret-0123456789abcdef",
            Duration.ofMinutes(5), Duration.ofHours(24), Clock.systemUTC());
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StreamingController(storage, playReporter, new CurrentUser(), tokens, catalog))
                .setControllerAdvice(new StreamingExceptionHandler())
                .build();
    }

    @Test
    void issuesATokenForAPublishedTrack() throws Exception {
        when(catalog.getTrackInfo(7L)).thenReturn(track(TrackStatus.TRACK_STATUS_PUBLISHED));

        mockMvc.perform(post("/api/v1/streaming/7/token").header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("STREAM"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.streamUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/streaming/7?token=")));
    }

    @Test
    void refusesATokenForATrackWithoutAudio() throws Exception {
        when(catalog.getTrackInfo(7L)).thenReturn(track(TrackStatus.TRACK_STATUS_PROCESSING));

        mockMvc.perform(post("/api/v1/streaming/7/token").header("X-User-Id", "42"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRACK_NOT_PLAYABLE"));
    }

    @Test
    void unknownTrackIsNotFound() throws Exception {
        when(catalog.getTrackInfo(7L)).thenThrow(Status.NOT_FOUND.asRuntimeException());

        mockMvc.perform(post("/api/v1/streaming/7/token").header("X-User-Id", "42"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenNeedsAnAuthenticatedCaller() throws Exception {
        mockMvc.perform(post("/api/v1/streaming/7/token")).andExpect(status().isUnauthorized());
    }

    @Test
    void streamsTheWholeFileAndCountsAPlay() throws Exception {
        when(storage.objectSize(KEY)).thenReturn((long) AUDIO.length);
        when(storage.getAudioStream(KEY)).thenReturn(new ByteArrayInputStream(AUDIO));

        mockMvc.perform(get("/api/v1/streaming/7").param("token", token(7)))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Length", "10"))
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andExpect(content().bytes(AUDIO));
        verify(playReporter).recordPlay(7L, 42L, 0);
    }

    @Test
    void streamsAByteRangeWithoutCountingASeekAsAPlay() throws Exception {
        when(storage.objectSize(KEY)).thenReturn((long) AUDIO.length);
        when(storage.getAudioStream(KEY, 4, 3)).thenReturn(new ByteArrayInputStream("456".getBytes()));

        mockMvc.perform(get("/api/v1/streaming/7").param("token", token(7)).header("Range", "bytes=4-6"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 4-6/10"))
                .andExpect(header().string("Content-Length", "3"))
                .andExpect(content().string("456"));
        verify(playReporter, never()).recordPlay(anyLong(), any(), anyInt());
    }

    @Test
    void rangeOutsideTheFileIs416() throws Exception {
        when(storage.objectSize(KEY)).thenReturn((long) AUDIO.length);

        mockMvc.perform(get("/api/v1/streaming/7").param("token", token(7)).header("Range", "bytes=50-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */10"));
    }

    @Test
    void offlineTokenDownloadsAsAttachment() throws Exception {
        when(storage.objectSize(KEY)).thenReturn((long) AUDIO.length);
        when(storage.getAudioStream(KEY)).thenReturn(new ByteArrayInputStream(AUDIO));
        String offline = tokens.issue(42, 7, StreamTokenService.Type.OFFLINE).token();

        mockMvc.perform(get("/api/v1/streaming/7").param("token", offline))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment")));
    }

    @Test
    void missingWrongOrForeignTokensAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/streaming/7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_STREAM_TOKEN"));
        mockMvc.perform(get("/api/v1/streaming/7").param("token", "not-a-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/streaming/7").param("token", token(8)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STREAM_TOKEN_MISMATCH"));
        verify(storage, never()).objectSize(eq(KEY));
    }

    private String token(long trackId) {
        return tokens.issue(42, trackId, StreamTokenService.Type.STREAM).token();
    }

    private static TrackInfo track(TrackStatus status) {
        return TrackInfo.newBuilder().setTrackId(7).setArtistId(1).setStatus(status).build();
    }
}

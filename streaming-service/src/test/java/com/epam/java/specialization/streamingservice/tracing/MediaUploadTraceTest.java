package com.epam.java.specialization.streamingservice.tracing;

import com.epam.java.specialization.streamingservice.outbox.OutboxEventRepository;
import com.epam.java.specialization.streamingservice.repository.TranscodingJobRepository;
import com.epam.java.specialization.streamingservice.service.StreamingStorageService;
import com.github.tomakehurst.wiremock.WireMockServer;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An upload checks the track's owner in catalog-service over HTTP. The call carries the upload's trace
 * ({@code traceparent}), and the queued job keeps it for the worker.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streaming-upload;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "catalog-client.internal-token=test-token",
})
@AutoConfigureMockMvc
@AutoConfigureTracing
class MediaUploadTraceTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
    private static final long ARTIST_ID = 7L;

    static final WireMockServer catalogService = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        catalogService.start();
    }

    @DynamicPropertySource
    static void catalogServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("catalog-client.base-url", catalogService::baseUrl);
    }

    @AfterAll
    static void stopCatalogService() {
        catalogService.stop();
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
        catalogService.resetAll();
        outboxRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void uploadContinuesTheIncomingTraceInCatalogServiceAndInTheJob() throws Exception {
        stubTrack(5, ARTIST_ID);

        mockMvc.perform(upload(5).header("traceparent", TRACEPARENT).header("X-User-Id", ARTIST_ID))
                .andExpect(status().isAccepted());

        catalogService.verify(getRequestedFor(urlPathEqualTo("/internal/tracks/5"))
                .withHeader("traceparent", containing(TRACE_ID))
                .withHeader("X-Internal-Token", equalTo("test-token")));
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
        catalogService.stubFor(get(urlPathEqualTo("/internal/tracks/8")).willReturn(aResponse().withStatus(404)));

        mockMvc.perform(upload(8).header("X-User-Id", ARTIST_ID)).andExpect(status().isNotFound());
    }

    @Test
    void uploadWithoutAUserIsUnauthorized() throws Exception {
        mockMvc.perform(upload(5)).andExpect(status().isUnauthorized());

        catalogService.verify(0, getRequestedFor(urlPathEqualTo("/internal/tracks/5")));
    }

    private static MockMultipartHttpServletRequestBuilder upload(long trackId) {
        MockMultipartFile file = new MockMultipartFile("file", "master.mp3", "audio/mpeg", new byte[1024]);
        return multipart("/api/v1/media/{trackId}/master", trackId).file(file);
    }

    private static void stubTrack(long trackId, long artistId) {
        catalogService.stubFor(get(urlPathEqualTo("/internal/tracks/" + trackId)).willReturn(okJson(
                "{\"id\":" + trackId + ",\"artistId\":" + artistId + ",\"status\":\"AWAITING_AUDIO\"}")));
    }
}

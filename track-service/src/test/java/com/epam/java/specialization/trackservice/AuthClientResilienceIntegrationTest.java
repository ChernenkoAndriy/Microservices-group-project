package com.epam.java.specialization.trackservice;

import com.epam.java.specialization.trackservice.client.ArtistProfiles;
import com.epam.java.specialization.trackservice.client.AuthClientConfig;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import com.epam.java.specialization.trackservice.repository.GenreRepository;
import com.epam.java.specialization.trackservice.repository.TrackRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "internal-api.token=test-token",
        "auth-client.internal-token=test-token",
        "resilience4j.retry.instances.auth-service.wait-duration=10ms",
        "resilience4j.circuitbreaker.instances.auth-service.wait-duration-in-open-state=60s",
})
@AutoConfigureMockMvc
@AutoConfigureTracing
class AuthClientResilienceIntegrationTest {

    private static final String USERS_PATH = "/internal/users";
    private static final long ARTIST_ID = 7L;
    private static final String ARTIST_7 = """
            [{"id":7,"email":"nina@example.com","displayName":"Nina Night","role":"ARTIST","status":"ACTIVE",
              "createdAt":"2026-10-01T10:00:00Z"}]
            """;

    static final WireMockServer authService = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        authService.start();
    }

    @DynamicPropertySource
    static void authServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("auth-client.base-url", authService::baseUrl);
    }

    @AfterAll
    static void stopAuthService() {
        authService.stop();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CircuitBreakerRegistry circuitBreakers;
    private CircuitBreaker authCircuitBreaker;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private GenreRepository genreRepository;

    @BeforeEach
    void reset() {
        authCircuitBreaker = circuitBreakers.circuitBreaker(AuthClientConfig.AUTH_SERVICE);
        authService.resetAll();
        authCircuitBreaker.reset();
        trackRepository.deleteAll();
    }

    @Nested
    class CircuitBreakerAndFallback {

        @Test
        void errorStormOpensTheCircuitAndTracksAreServedWithTheFallbackName() throws Exception {
            authService.stubFor(get(urlPathEqualTo(USERS_PATH)).willReturn(aResponse().withStatus(500)));
            Long trackId = publishedTrack(ARTIST_ID, "Storm").getId();

            for (int i = 0; i < 2; i++) {
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/tracks/{id}", trackId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.artist.id").value(ARTIST_ID))
                        .andExpect(jsonPath("$.artist.name").value(ArtistProfiles.UNKNOWN_ARTIST));
            }
            assertThat(authCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            int callsWhenOpened = authService.countRequestsMatching(getRequestedFor(urlPathEqualTo(USERS_PATH))
                    .build()).getCount();
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/v1/tracks/{id}", trackId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.artist.name").value(ArtistProfiles.UNKNOWN_ARTIST));
            assertThat(authService.countRequestsMatching(getRequestedFor(urlPathEqualTo(USERS_PATH)).build())
                    .getCount()).isEqualTo(callsWhenOpened);
        }

        @Test
        void clientErrorsAreNotRetriedNorCountedAsFailures() throws Exception {
            authService.stubFor(get(urlPathEqualTo(USERS_PATH)).willReturn(aResponse().withStatus(400)));
            Long trackId = publishedTrack(ARTIST_ID, "Bad request").getId();

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/v1/tracks/{id}", trackId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.artist.name").value(ArtistProfiles.UNKNOWN_ARTIST));

            authService.verify(1, getRequestedFor(urlPathEqualTo(USERS_PATH)));
            assertThat(authCircuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        }

        @Test
        void artistPageWithoutProfileOrTracksIsProblemDetail503() throws Exception {
            authService.stubFor(get(urlPathEqualTo(USERS_PATH)).willReturn(aResponse().withStatus(503)));

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/v1/artists/{id}", 42))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.code").value("ARTIST_PROFILE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.instance").value("/api/v1/artists/42"));
        }
    }

    @Nested
    class Retry {

        @Test
        void twoTransientFailuresThenSuccessMakesExactlyThreeCalls() throws Exception {
            authService.stubFor(get(urlPathEqualTo(USERS_PATH)).inScenario("flaky")
                    .whenScenarioStateIs(Scenario.STARTED).willReturn(aResponse().withStatus(500))
                    .willSetStateTo("failed once"));
            authService.stubFor(get(urlPathEqualTo(USERS_PATH)).inScenario("flaky")
                    .whenScenarioStateIs("failed once").willReturn(aResponse().withStatus(502))
                    .willSetStateTo("failed twice"));
            authService.stubFor(get(urlPathEqualTo(USERS_PATH)).inScenario("flaky")
                    .whenScenarioStateIs("failed twice").willReturn(okJson(ARTIST_7)));
            Long trackId = publishedTrack(ARTIST_ID, "Second wind").getId();

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/v1/tracks/{id}", trackId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.artist.name").value("Nina Night"));

            authService.verify(3, getRequestedFor(urlPathEqualTo(USERS_PATH)));
            assertThat(authCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    @Nested
    class BatchLookup {

        @Test
        void aPageOfTracksByDifferentArtistsNeedsOneCallToAuthService() throws Exception {
            authService.stubFor(get(urlPathEqualTo(USERS_PATH)).willReturn(okJson("""
                    [{"id":7,"email":"nina@example.com","displayName":"Nina Night","role":"ARTIST","status":"ACTIVE",
                      "createdAt":"2026-10-01T10:00:00Z"},
                     {"id":8,"email":"otto@example.com","displayName":"Otto","role":"ARTIST","status":"ACTIVE",
                      "createdAt":"2026-10-01T10:00:00Z"}]
                    """)));
            Long a = publishedTrack(7L, "One").getId();
            Long b = publishedTrack(8L, "Two").getId();
            Long c = publishedTrack(7L, "Three").getId();

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/v1/tracks").param("ids", a + "," + b + "," + c))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.items[?(@.artist.id == 8)].artist.name").value("Otto"))
                    .andExpect(jsonPath("$.page.totalElements").value(3));

            authService.verify(1, getRequestedFor(urlPathEqualTo(USERS_PATH)));
        }
    }

    @Nested
    class TraceHeaders {

        @Test
        void traceContextAndCorrelationIdReachAuthService() throws Exception {
            authService.stubFor(get(urlPathEqualTo(USERS_PATH)).willReturn(okJson(ARTIST_7)));
            Long trackId = publishedTrack(ARTIST_ID, "Traced").getId();

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/v1/tracks/{id}", trackId)
                            .header("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
                            .header("X-Correlation-Id", "corr-123"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Correlation-Id", "corr-123"));

            authService.verify(getRequestedFor(urlPathEqualTo(USERS_PATH))
                    .withHeader("traceparent", containing("0af7651916cd43dd8448eb211c80319c"))
                    .withHeader("X-Correlation-Id", equalTo("corr-123"))
                    .withHeader("X-Internal-Token", equalTo("test-token")));
        }
    }

    @Nested
    class ProblemDetails {

        @Test
        void unknownTrackIsProblemJson() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/v1/tracks/{id}", 999_999))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Not Found"))
                    .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));
        }

        @Test
        void invalidBodyListsFieldErrors() throws Exception {
            mockMvc.perform(post("/api/v1/tracks").header("X-User-Id", "7").header("X-User-Roles", "ARTIST")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"title":" padded ","genreSlugs":[]}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.length()").value(2));
        }

        @Test
        void listenerCannotCreateTracks() throws Exception {
            mockMvc.perform(post("/api/v1/tracks").header("X-User-Id", "7").header("X-User-Roles", "LISTENER")
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_TRACK))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        void notImplementedOperationAnswers501() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/v1/albums/{id}", 1))
                    .andExpect(status().isNotImplemented());
        }
    }

    @Nested
    class Idempotency {

        @Test
        void repeatedCreateWithTheSameKeyReturnsTheFirstTrack() throws Exception {
            authService.stubFor(get(urlPathEqualTo(USERS_PATH)).willReturn(okJson(ARTIST_7)));

            String first = createTrack("key-1", CREATE_TRACK)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("AWAITING_AUDIO"))
                    .andReturn().getResponse().getContentAsString();
            createTrack("key-1", CREATE_TRACK)
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Idempotent-Replayed", "true"))
                    .andExpect(content().json(first));

            assertThat(trackRepository.count()).isEqualTo(1);
        }

        @Test
        void reusingAKeyWithAnotherBodyIsRejected() throws Exception {
            authService.stubFor(get(urlPathEqualTo(USERS_PATH)).willReturn(okJson(ARTIST_7)));
            createTrack("key-2", CREATE_TRACK).andExpect(status().isCreated());

            createTrack("key-2", CREATE_TRACK.replace("Night Drive", "Day Drive"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
            assertThat(trackRepository.count()).isEqualTo(1);
        }

        private org.springframework.test.web.servlet.ResultActions createTrack(String key, String body)
                throws Exception {
            return mockMvc.perform(post("/api/v1/tracks").header("X-User-Id", "7").header("X-User-Roles", "ARTIST")
                    .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body));
        }
    }

    @Nested
    class InternalApi {

        @Test
        void publishRequiresTheServiceToken() throws Exception {
            Long trackId = trackRepository.save(track(ARTIST_ID, "Unreleased", TrackStatus.AWAITING_AUDIO)).getId();

            mockMvc.perform(post("/internal/tracks/{id}/publish", trackId)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"durationSeconds\":180}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_TOKEN"));

            mockMvc.perform(post("/internal/tracks/{id}/publish", trackId).header("X-Internal-Token", "test-token")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"durationSeconds\":180}"))
                    .andExpect(status().isNoContent());
            assertThat(trackRepository.findById(trackId).orElseThrow().getStatus()).isEqualTo(TrackStatus.PUBLISHED);
        }
    }

    private static final String CREATE_TRACK = """
            {"title":"Night Drive","genreSlugs":["electronic","ambient"]}
            """;

    private Track publishedTrack(Long artistId, String title) {
        return trackRepository.save(track(artistId, title, TrackStatus.PUBLISHED));
    }

    private Track track(Long artistId, String title, TrackStatus status) {
        Track track = new Track();
        track.setTitle(title);
        track.setArtistId(artistId);
        track.setGenres(List.of(genreRepository.findById("pop").orElseThrow()));
        track.setStatus(status);
        if (status == TrackStatus.PUBLISHED) {
            track.setPublishedAt(Instant.now());
        }
        return track;
    }
}

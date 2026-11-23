package com.epam.java.specialization.trackservice;

import com.epam.java.specialization.trackservice.client.ArtistProfiles;
import com.epam.java.specialization.trackservice.client.AuthClientConfig;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import com.epam.java.specialization.trackservice.repository.GenreRepository;
import com.epam.java.specialization.trackservice.repository.TrackRepository;
import com.epam.java.specialization.trackservice.support.PostgresTestcontainer;
import com.github.tomakehurst.wiremock.WireMockServer;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.Testcontainers;
import org.testcontainers.toxiproxy.ToxiproxyContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaos test of the catalog -> auth-service call: Toxiproxy (in Docker) sits between catalog-service and
 * a stubbed auth-service and breaks the network on demand. A latency longer than the read timeout and a cut
 * connection must open the Circuit Breaker and serve tracks with the fallback artist name instead of failing;
 * once the network is healthy again the breaker closes by itself.
 */
@SpringBootTest(properties = {
        "auth-client.internal-token=test-token",
        "auth-client.read-timeout=300ms",
        "resilience4j.retry.instances.auth-service.wait-duration=10ms",
        "resilience4j.circuitbreaker.instances.auth-service.wait-duration-in-open-state=1s",
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class AuthServiceNetworkFaultTest {

    private static final String USERS_PATH = "/internal/users";
    private static final long ARTIST_ID = 7L;
    private static final int PROXY_PORT = 8666;
    private static final String ARTIST_7 = """
            [{"id":7,"email":"nina@example.com","displayName":"Nina Night","role":"ARTIST","status":"ACTIVE",
              "createdAt":"2026-10-01T10:00:00Z"}]
            """;

    static final WireMockServer authService = new WireMockServer(wireMockConfig().dynamicPort());
    static final ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.12.0");
    static final Proxy authProxy;

    static {
        authService.start();
        // Lets the Toxiproxy container reach the WireMock server that runs in this JVM.
        Testcontainers.exposeHostPorts(authService.port());
        toxiproxy.start();
        try {
            authProxy = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort())
                    .createProxy("auth-service", "0.0.0.0:" + PROXY_PORT,
                            "host.testcontainers.internal:" + authService.port());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void authServiceBehindToxiproxy(DynamicPropertyRegistry registry) {
        registry.add("auth-client.base-url",
                () -> "http://" + toxiproxy.getHost() + ":" + toxiproxy.getMappedPort(PROXY_PORT));
    }

    @AfterAll
    static void stopAll() {
        toxiproxy.stop();
        authService.stop();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CircuitBreakerRegistry circuitBreakers;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private GenreRepository genreRepository;

    private CircuitBreaker authCircuitBreaker;
    private Long trackId;

    @BeforeEach
    void setUp() {
        authService.resetAll();
        authService.stubFor(get(urlPathEqualTo(USERS_PATH)).willReturn(okJson(ARTIST_7)));
        authCircuitBreaker = circuitBreakers.circuitBreaker(AuthClientConfig.AUTH_SERVICE);
        authCircuitBreaker.reset();
        trackRepository.deleteAll();
        trackId = trackRepository.save(publishedTrack()).getId();
    }

    @AfterEach
    void healTheNetwork() throws IOException {
        authProxy.enable();
        for (var toxic : authProxy.toxics().getAll()) {
            toxic.remove();
        }
    }

    @Test
    void healthyNetworkGoesThroughTheProxy() throws Exception {
        expectArtistName("Nina Night");

        authService.verify(getRequestedFor(urlPathEqualTo(USERS_PATH)));
        assertThat(authCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void slowNetworkOpensTheCircuitAndTheFallbackAnswersInstead() throws Exception {
        authProxy.toxics().latency("slow-auth", ToxicDirection.DOWNSTREAM, 1_000);

        // Every attempt runs into the 300 ms read timeout; the track is still served, with the fallback name.
        for (int i = 0; i < 2; i++) {
            expectArtistName(ArtistProfiles.UNKNOWN_ARTIST);
        }
        assertThat(authCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // While OPEN the call is short-circuited: no request reaches auth-service and no timeout is waited for.
        int callsWhenOpened = authService.getAllServeEvents().size();
        Instant start = Instant.now();
        expectArtistName(ArtistProfiles.UNKNOWN_ARTIST);
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofMillis(300));
        assertThat(authService.getAllServeEvents()).hasSize(callsWhenOpened);

        // The network recovers: after the open-state wait the breaker lets trial calls through and closes.
        authProxy.toxics().get("slow-auth").remove();
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> expectArtistName("Nina Night"));
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    expectArtistName("Nina Night");
                    assertThat(authCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
                });
    }

    @Test
    void cutConnectionIsHandledByTheFallback() throws Exception {
        authProxy.disable();

        for (int i = 0; i < 2; i++) {
            expectArtistName(ArtistProfiles.UNKNOWN_ARTIST);
        }

        assertThat(authCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(authService.getAllServeEvents()).isEmpty();
    }

    private void expectArtistName(String name) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/tracks/{id}", trackId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artist.id").value(ARTIST_ID))
                .andExpect(jsonPath("$.artist.name").value(name));
    }

    private Track publishedTrack() {
        Track track = new Track();
        track.setTitle("Packet Loss");
        track.setArtistId(ARTIST_ID);
        track.setGenres(List.of(genreRepository.findById("pop").orElseThrow()));
        track.setStatus(TrackStatus.PUBLISHED);
        track.setPublishedAt(Instant.now());
        return track;
    }
}

package com.epam.java.specialization.streamingservice.client;

import com.epam.java.specialization.grpc.catalog.v1.PlayEvent;
import com.epam.java.specialization.grpc.catalog.v1.PlaySummary;
import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import com.epam.java.specialization.grpc.catalog.v1.TrackPlayCount;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatus;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatusUpdate;
import com.epam.java.specialization.streamingservice.service.PlayReporter;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * The four calls of {@link CatalogGrpcClient} against a fake catalog-service on a free localhost port.
 * <p>
 * Only the channel's target is overridden: the channel is built by Spring from application.yaml, so the retry
 * policy (service-config), the default deadline and the interceptors are the ones used in production.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streaming-grpc-client;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "catalog-grpc.internal-token=" + CatalogGrpcClientTest.TOKEN,
        // Flushed by the tests themselves.
        "catalog-grpc.play-report.interval-ms=3600000",
})
@ExtendWith(OutputCaptureExtension.class)
class CatalogGrpcClientTest {

    static final String TOKEN = "client-test-token";
    static final FakeCatalogService catalog = FakeCatalogService.start();

    @DynamicPropertySource
    static void catalogTarget(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.client.channel.catalog.target", catalog::target);
    }

    @AfterAll
    static void stopCatalog() {
        catalog.stop();
    }

    @Autowired
    private CatalogGrpcClient client;
    @Autowired
    private PlayReporter playReporter;

    @BeforeEach
    void resetCatalog() {
        catalog.reset();
        catalog.addTrack(1, 7, TrackStatus.TRACK_STATUS_PUBLISHED);
        catalog.addTrack(2, 7, TrackStatus.TRACK_STATUS_AWAITING_AUDIO);
        catalog.addTrack(3, 8, TrackStatus.TRACK_STATUS_PUBLISHED);
    }

    // ---- the four kinds of calls ----

    @Test
    void getTrackInfoReturnsTheTrackAndSendsTheToken() {
        TrackInfo track = client.getTrackInfo(1);

        assertThat(track.getId()).isEqualTo(1);
        assertThat(track.getArtistId()).isEqualTo(7);
        assertThat(track.getStatus()).isEqualTo(TrackStatus.TRACK_STATUS_PUBLISHED);
        assertThat(catalog.tokens()).containsOnly(TOKEN);
    }

    @Test
    void getTrackInfoOfUnknownTrackThrowsNotFoundWithoutRetrying() {
        assertCode(() -> client.getTrackInfo(99), Status.Code.NOT_FOUND);
        assertThat(catalog.attempts("GetTrackInfo")).isEqualTo(1);
    }

    @Test
    void listArtistTracksCollectsTheStreamedTracks() {
        assertThat(client.listArtistTracks(7)).extracting(TrackInfo::getId).containsExactly(1L, 2L);
        assertThat(client.listArtistTracks(42)).isEmpty();
    }

    @Test
    void reportPlaysStreamsEveryEventAndReturnsTheSummary() {
        PlaySummary summary = client.reportPlays(List.of(play(1), play(3), play(1)));

        assertThat(summary.getEventsReceived()).isEqualTo(3);
        assertThat(summary.getTracksList()).extracting(TrackPlayCount::getTrackId, TrackPlayCount::getPlaysAdded)
                .containsExactly(tuple(1L, 2), tuple(3L, 1));
        assertThat(catalog.reportedPlays()).extracting(PlayEvent::getTrackId).containsExactly(1L, 3L, 1L);
        assertThat(catalog.tokens()).containsOnly(TOKEN);
    }

    @Test
    void watchTrackStatusReturnsOneUpdatePerRequestedTrack() {
        List<TrackStatusUpdate> updates = client.watchTrackStatus(List.of(2L, 1L));

        assertThat(updates).extracting(TrackStatusUpdate::getTrackId, TrackStatusUpdate::getStatus)
                .containsExactly(tuple(2L, TrackStatus.TRACK_STATUS_AWAITING_AUDIO),
                        tuple(1L, TrackStatus.TRACK_STATUS_PUBLISHED));
    }

    @Test
    void watchTrackStatusPassesOnTheServersError() {
        assertCode(() -> client.watchTrackStatus(List.of(1L, 99L)), Status.Code.NOT_FOUND);
    }

    // ---- retries (service-config of application.yaml) ----

    @Test
    void getTrackInfoIsRetriedOnUnavailableAndSucceedsOnTheThirdAttempt(CapturedOutput output) {
        catalog.failNext("GetTrackInfo", 2, Status.UNAVAILABLE);

        TrackInfo track = client.getTrackInfo(1);

        assertThat(track.getId()).isEqualTo(1);
        assertThat(catalog.attempts("GetTrackInfo")).isEqualTo(3);
        // grpc-java marks every retry with the number of attempts before it.
        assertThat(catalog.previousAttemptsHeaders("GetTrackInfo")).containsExactly(null, "1", "2");
        assertThat(output)
                .contains("GetTrackInfo attempt 1 -> UNAVAILABLE")
                .contains("GetTrackInfo attempt 2 -> UNAVAILABLE")
                .contains("GetTrackInfo -> OK");
    }

    @Test
    void getTrackInfoFailsWithUnavailableAfterMaxAttempts(CapturedOutput output) {
        catalog.failNext("GetTrackInfo", 10, Status.UNAVAILABLE);

        assertCode(() -> client.getTrackInfo(1), Status.Code.UNAVAILABLE);
        assertThat(catalog.attempts("GetTrackInfo")).isEqualTo(3);
        assertThat(output).contains("GetTrackInfo attempt 3 -> UNAVAILABLE");
    }

    @Test
    void otherErrorsAreNotRetried() {
        catalog.failNext("GetTrackInfo", 10, Status.INTERNAL);

        assertCode(() -> client.getTrackInfo(1), Status.Code.INTERNAL);
        assertThat(catalog.attempts("GetTrackInfo")).isEqualTo(1);
    }

    @Test
    void listArtistTracksIsRetriedOnUnavailable() {
        catalog.failNext("ListArtistTracks", 1, Status.UNAVAILABLE);

        assertThat(client.listArtistTracks(7)).extracting(TrackInfo::getId).containsExactly(1L, 2L);
        assertThat(catalog.attempts("ListArtistTracks")).isEqualTo(2);
    }

    @Test
    void watchTrackStatusIsRetriedOnUnavailable() {
        catalog.failNext("WatchTrackStatus", 2, Status.UNAVAILABLE);

        assertThat(client.watchTrackStatus(List.of(1L, 3L))).extracting(TrackStatusUpdate::getTrackId)
                .containsExactly(1L, 3L);
        assertThat(catalog.attempts("WatchTrackStatus")).isEqualTo(3);
    }

    @Test
    void reportPlaysIsNeverRetried() {
        catalog.failNext("ReportPlays", 10, Status.UNAVAILABLE);

        assertCode(() -> client.reportPlays(List.of(play(1), play(3))), Status.Code.UNAVAILABLE);
        assertThat(catalog.attempts("ReportPlays")).isEqualTo(1);
    }

    @Test
    void callsAreBoundedByTheDefaultDeadline() {
        catalog.hangNext("GetTrackInfo");

        long start = System.nanoTime();
        assertCode(() -> client.getTrackInfo(1), Status.Code.DEADLINE_EXCEEDED);
        // application.yaml: spring.grpc.client.channel.catalog.default.deadline=3s
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isBetween(Duration.ofMillis(2500), Duration.ofSeconds(10));
    }

    // ---- PlayReporter: the production caller of ReportPlays ----

    @Test
    void playReporterSendsTheBufferedPlaysAsOneCall() {
        playReporter.recordPlay(1, 5L, 0);
        playReporter.recordPlay(3, null, 0);

        playReporter.flush();

        assertThat(catalog.attempts("ReportPlays")).isEqualTo(1);
        assertThat(catalog.reportedPlays()).extracting(PlayEvent::getTrackId, PlayEvent::getUserId)
                .containsExactly(tuple(1L, 5L), tuple(3L, 0L));
        playReporter.flush();
        assertThat(catalog.attempts("ReportPlays")).as("nothing left to send").isEqualTo(1);
    }

    @Test
    void playReporterDropsAFailedBatchInsteadOfSendingItTwice(CapturedOutput output) {
        catalog.failNext("ReportPlays", 1, Status.UNAVAILABLE);
        playReporter.recordPlay(1, 5L, 0);

        playReporter.flush();
        playReporter.flush();

        assertThat(catalog.attempts("ReportPlays")).isEqualTo(1);
        assertThat(output).contains("Dropped 1 play events");
    }

    private static PlayEvent play(long trackId) {
        return PlayEvent.newBuilder().setTrackId(trackId).setUserId(5).build();
    }

    private static void assertCode(Runnable call, Status.Code expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(expected);
    }
}

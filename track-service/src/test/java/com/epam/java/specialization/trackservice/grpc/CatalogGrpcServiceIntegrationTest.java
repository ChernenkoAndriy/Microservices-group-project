package com.epam.java.specialization.trackservice.grpc;

import com.epam.java.specialization.grpc.catalog.v1.CatalogInternalServiceGrpc;
import com.epam.java.specialization.grpc.catalog.v1.GetTrackInfoRequest;
import com.epam.java.specialization.grpc.catalog.v1.ListArtistTracksRequest;
import com.epam.java.specialization.grpc.catalog.v1.PlayEvent;
import com.epam.java.specialization.grpc.catalog.v1.PlaySummary;
import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import com.epam.java.specialization.grpc.catalog.v1.TrackPlayCount;
import com.epam.java.specialization.grpc.catalog.v1.TrackStatusUpdate;
import com.epam.java.specialization.grpc.catalog.v1.WatchTrackStatusRequest;
import com.epam.java.specialization.trackservice.model.Track;
import com.epam.java.specialization.trackservice.model.TrackStatus;
import com.epam.java.specialization.trackservice.repository.TrackRepository;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static com.epam.java.specialization.grpc.catalog.v1.TrackStatus.TRACK_STATUS_AWAITING_AUDIO;
import static com.epam.java.specialization.grpc.catalog.v1.TrackStatus.TRACK_STATUS_PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calls every method of catalog_internal.proto through the real gRPC server of the application (in-process
 * transport, so no port is opened): interceptors, exception handler, service and database all take part.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-grpc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.grpc.server.inprocess.name=" + CatalogGrpcServiceIntegrationTest.SERVER_NAME,
        "internal-api.token=" + CatalogGrpcServiceIntegrationTest.TOKEN,
})
@ExtendWith(OutputCaptureExtension.class)
class CatalogGrpcServiceIntegrationTest {

    static final String SERVER_NAME = "catalog-grpc-test";
    static final String TOKEN = "test-internal-token";
    private static final AtomicLong ARTIST_IDS = new AtomicLong(1000);

    @Autowired
    private TrackRepository trackRepository;

    private ManagedChannel channel;

    @BeforeEach
    void openChannel() {
        channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
    }

    @AfterEach
    void closeChannel() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    // ---- GetTrackInfo (unary) ----

    @Test
    void getTrackInfoReturnsTheTrack() {
        long artistId = ARTIST_IDS.incrementAndGet();
        Track track = saveTrack(artistId, "Song", TrackStatus.PUBLISHED, 180);

        TrackInfo info = blockingStub(TOKEN).getTrackInfo(GetTrackInfoRequest.newBuilder().setTrackId(track.getId()).build());

        assertThat(info.getId()).isEqualTo(track.getId());
        assertThat(info.getArtistId()).isEqualTo(artistId);
        assertThat(info.getStatus()).isEqualTo(TRACK_STATUS_PUBLISHED);
        assertThat(info.getTitle()).isEqualTo("Song");
        assertThat(info.hasDurationSeconds()).isTrue();
        assertThat(info.getDurationSeconds()).isEqualTo(180);
        assertThat(info.getPlayCount()).isZero();
    }

    @Test
    void getTrackInfoLeavesDurationUnsetUntilTranscoded() {
        Track track = saveTrack(ARTIST_IDS.incrementAndGet(), "Draft", TrackStatus.AWAITING_AUDIO, null);

        TrackInfo info = blockingStub(TOKEN).getTrackInfo(GetTrackInfoRequest.newBuilder().setTrackId(track.getId()).build());

        assertThat(info.getStatus()).isEqualTo(TRACK_STATUS_AWAITING_AUDIO);
        assertThat(info.hasDurationSeconds()).isFalse();
    }

    @Test
    void getTrackInfoOfUnknownTrackIsNotFound(CapturedOutput output) {
        assertStatus(() -> blockingStub(TOKEN).getTrackInfo(GetTrackInfoRequest.newBuilder().setTrackId(987_654).build()),
                Status.Code.NOT_FOUND);
        // The logging interceptor is outside the exception handler, so it sees the mapped status.
        assertThat(output).contains("GetTrackInfo -> NOT_FOUND");
    }

    @Test
    void getTrackInfoWithoutTrackIdIsInvalidArgument() {
        assertStatus(() -> blockingStub(TOKEN).getTrackInfo(GetTrackInfoRequest.getDefaultInstance()),
                Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void callWithoutTokenIsUnauthenticated() {
        Track track = saveTrack(ARTIST_IDS.incrementAndGet(), "Song", TrackStatus.PUBLISHED, 100);
        GetTrackInfoRequest request = GetTrackInfoRequest.newBuilder().setTrackId(track.getId()).build();

        assertStatus(() -> CatalogInternalServiceGrpc.newBlockingStub(channel).getTrackInfo(request),
                Status.Code.UNAUTHENTICATED);
    }

    @Test
    void callWithWrongTokenIsUnauthenticated() {
        Track track = saveTrack(ARTIST_IDS.incrementAndGet(), "Song", TrackStatus.PUBLISHED, 100);
        GetTrackInfoRequest request = GetTrackInfoRequest.newBuilder().setTrackId(track.getId()).build();

        assertStatus(() -> blockingStub("wrong-token").getTrackInfo(request), Status.Code.UNAUTHENTICATED);
    }

    @Test
    void streamingCallWithoutTokenIsUnauthenticated() {
        Recorder<PlaySummary> summary = new Recorder<>();
        StreamObserver<PlayEvent> requests = CatalogInternalServiceGrpc.newStub(channel).reportPlays(summary);
        requests.onNext(play(1));
        requests.onCompleted();

        assertThat(summary.error()).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    // ---- ListArtistTracks (server streaming) ----

    @Test
    void listArtistTracksStreamsEveryTrackOfTheArtistOldestFirst() {
        long artistId = ARTIST_IDS.incrementAndGet();
        Track first = saveTrack(artistId, "First", TrackStatus.PUBLISHED, 120);
        Track second = saveTrack(artistId, "Second", TrackStatus.AWAITING_AUDIO, null);
        Track third = saveTrack(artistId, "Third", TrackStatus.UNPUBLISHED, 90);
        saveTrack(ARTIST_IDS.incrementAndGet(), "Someone else's", TrackStatus.PUBLISHED, 60);

        List<TrackInfo> tracks = new ArrayList<>();
        blockingStub(TOKEN).listArtistTracks(ListArtistTracksRequest.newBuilder().setArtistId(artistId).build())
                .forEachRemaining(tracks::add);

        assertThat(tracks).extracting(TrackInfo::getId).containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(tracks).extracting(TrackInfo::getArtistId).containsOnly(artistId);
    }

    @Test
    void listArtistTracksOfArtistWithoutTracksIsEmpty() {
        List<TrackInfo> tracks = new ArrayList<>();
        blockingStub(TOKEN).listArtistTracks(ListArtistTracksRequest.newBuilder()
                .setArtistId(ARTIST_IDS.incrementAndGet()).build()).forEachRemaining(tracks::add);

        assertThat(tracks).isEmpty();
    }

    @Test
    void listArtistTracksWithoutArtistIdIsInvalidArgument() {
        assertStatus(() -> blockingStub(TOKEN).listArtistTracks(ListArtistTracksRequest.getDefaultInstance()).hasNext(),
                Status.Code.INVALID_ARGUMENT);
    }

    // ---- ReportPlays (client streaming) ----

    @Test
    void reportPlaysAddsOnePlayPerEventAndReturnsTheTotals() {
        long artistId = ARTIST_IDS.incrementAndGet();
        Track a = saveTrack(artistId, "A", TrackStatus.PUBLISHED, 100);
        Track b = saveTrack(artistId, "B", TrackStatus.PUBLISHED, 100);

        PlaySummary summary = reportPlays(List.of(play(a.getId()), play(b.getId()), play(a.getId())));

        assertThat(summary.getEventsReceived()).isEqualTo(3);
        assertThat(summary.getTracksList()).extracting(TrackPlayCount::getTrackId, TrackPlayCount::getPlaysAdded,
                        TrackPlayCount::getTotalPlays)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(a.getId(), 2, 2L),
                        org.assertj.core.groups.Tuple.tuple(b.getId(), 1, 1L));

        // A second report adds to the counter, and GetTrackInfo shows it.
        PlaySummary next = reportPlays(List.of(play(a.getId())));
        assertThat(next.getTracks(0).getTotalPlays()).isEqualTo(3);
        assertThat(blockingStub(TOKEN).getTrackInfo(GetTrackInfoRequest.newBuilder().setTrackId(a.getId()).build())
                .getPlayCount()).isEqualTo(3);
    }

    @Test
    void reportPlaysWithoutEventsReturnsAnEmptySummary() {
        PlaySummary summary = reportPlays(List.of());

        assertThat(summary.getEventsReceived()).isZero();
        assertThat(summary.getTracksList()).isEmpty();
    }

    @Test
    void reportPlaysWithUnknownTrackIsNotFoundAndSavesNothing() {
        Track known = saveTrack(ARTIST_IDS.incrementAndGet(), "Known", TrackStatus.PUBLISHED, 100);

        Recorder<PlaySummary> summary = new Recorder<>();
        StreamObserver<PlayEvent> requests = asyncStub().reportPlays(summary);
        requests.onNext(play(known.getId()));
        requests.onNext(play(876_543));
        requests.onCompleted();

        assertThat(summary.error()).isEqualTo(Status.Code.NOT_FOUND);
        // All or nothing: the play of the known track was rolled back too.
        assertThat(trackRepository.findPlayCount(known.getId())).isZero();
    }

    @Test
    void reportPlaysWithInvalidEventIsInvalidArgumentAndSavesNothing() {
        Track known = saveTrack(ARTIST_IDS.incrementAndGet(), "Known", TrackStatus.PUBLISHED, 100);

        Recorder<PlaySummary> summary = new Recorder<>();
        StreamObserver<PlayEvent> requests = asyncStub().reportPlays(summary);
        requests.onNext(play(known.getId()));
        requests.onNext(play(known.getId()).toBuilder().setListenedSeconds(-1).build());
        requests.onNext(play(known.getId()));
        requests.onCompleted();

        assertThat(summary.error()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(trackRepository.findPlayCount(known.getId())).isZero();
    }

    @Test
    void reportPlaysWithoutTrackIdIsInvalidArgument() {
        Recorder<PlaySummary> summary = new Recorder<>();
        StreamObserver<PlayEvent> requests = asyncStub().reportPlays(summary);
        requests.onNext(PlayEvent.newBuilder().setUserId(1).build());
        requests.onCompleted();

        assertThat(summary.error()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // ---- WatchTrackStatus (bidirectional streaming) ----

    @Test
    void watchTrackStatusAnswersEveryRequestWithTheCurrentStatus() {
        long artistId = ARTIST_IDS.incrementAndGet();
        Track published = saveTrack(artistId, "Published", TrackStatus.PUBLISHED, 100);
        Track awaiting = saveTrack(artistId, "Awaiting", TrackStatus.AWAITING_AUDIO, null);

        Recorder<TrackStatusUpdate> updates = new Recorder<>();
        StreamObserver<WatchTrackStatusRequest> requests = asyncStub().watchTrackStatus(updates);
        requests.onNext(watch(published.getId()));
        requests.onNext(watch(awaiting.getId()));
        requests.onNext(watch(published.getId()));
        requests.onCompleted();

        List<TrackStatusUpdate> received = updates.values();
        assertThat(received).extracting(TrackStatusUpdate::getTrackId)
                .containsExactly(published.getId(), awaiting.getId(), published.getId());
        assertThat(received).extracting(TrackStatusUpdate::getStatus)
                .containsExactly(TRACK_STATUS_PUBLISHED, TRACK_STATUS_AWAITING_AUDIO, TRACK_STATUS_PUBLISHED);
        assertThat(received).allSatisfy(update -> assertThat(update.hasUpdatedAt()).isTrue());
    }

    @Test
    void watchTrackStatusWithoutRequestsCompletesEmpty() {
        Recorder<TrackStatusUpdate> updates = new Recorder<>();
        asyncStub().watchTrackStatus(updates).onCompleted();

        assertThat(updates.values()).isEmpty();
    }

    @Test
    void watchTrackStatusOfUnknownTrackEndsTheCallWithNotFound() {
        Track known = saveTrack(ARTIST_IDS.incrementAndGet(), "Known", TrackStatus.PUBLISHED, 100);

        Recorder<TrackStatusUpdate> updates = new Recorder<>();
        StreamObserver<WatchTrackStatusRequest> requests = asyncStub().watchTrackStatus(updates);
        requests.onNext(watch(known.getId()));
        requests.onNext(watch(765_432));
        requests.onNext(watch(known.getId()));
        requests.onCompleted();

        assertThat(updates.error()).isEqualTo(Status.Code.NOT_FOUND);
        // The update sent before the failure still reached the client; nothing after it.
        assertThat(updates.received).extracting(TrackStatusUpdate::getTrackId).containsExactly(known.getId());
    }

    // ---- helpers ----

    private Track saveTrack(long artistId, String title, TrackStatus status, Integer durationSeconds) {
        Track track = new Track();
        track.setArtistId(artistId);
        track.setTitle(title);
        track.setStatus(status);
        track.setDurationSeconds(durationSeconds);
        return trackRepository.saveAndFlush(track);
    }

    private PlaySummary reportPlays(List<PlayEvent> events) {
        Recorder<PlaySummary> summary = new Recorder<>();
        StreamObserver<PlayEvent> requests = asyncStub().reportPlays(summary);
        events.forEach(requests::onNext);
        requests.onCompleted();
        List<PlaySummary> values = summary.values();
        assertThat(values).hasSize(1);
        return values.getFirst();
    }

    private static PlayEvent play(long trackId) {
        return PlayEvent.newBuilder().setTrackId(trackId).setUserId(42).setListenedSeconds(30)
                .setPlayedAt(GrpcTrackMapper.toTimestamp(java.time.Instant.now())).build();
    }

    private static WatchTrackStatusRequest watch(long trackId) {
        return WatchTrackStatusRequest.newBuilder().setTrackId(trackId).build();
    }

    private CatalogInternalServiceGrpc.CatalogInternalServiceBlockingStub blockingStub(String token) {
        return CatalogInternalServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(tokenHeader(token)))
                .withDeadlineAfter(5, TimeUnit.SECONDS);
    }

    private CatalogInternalServiceGrpc.CatalogInternalServiceStub asyncStub() {
        return CatalogInternalServiceGrpc.newStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(tokenHeader(TOKEN)))
                .withDeadlineAfter(5, TimeUnit.SECONDS);
    }

    private static Metadata tokenHeader(String token) {
        Metadata headers = new Metadata();
        headers.put(InternalTokenServerInterceptor.INTERNAL_TOKEN, token);
        return headers;
    }

    private static void assertStatus(Runnable call, Status.Code expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isEqualTo(expected);
    }

    /** Collects the responses of a call and how it ended. */
    private static final class Recorder<T> implements StreamObserver<T> {

        private final List<T> received = Collections.synchronizedList(new ArrayList<>());
        private final CompletableFuture<List<T>> done = new CompletableFuture<>();

        @Override
        public void onNext(T value) {
            received.add(value);
        }

        @Override
        public void onError(Throwable t) {
            done.completeExceptionally(t);
        }

        @Override
        public void onCompleted() {
            done.complete(List.copyOf(received));
        }

        List<T> values() {
            return await(Function.identity());
        }

        Status.Code error() {
            try {
                done.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                return Status.fromThrowable(e.getCause()).getCode();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            throw new AssertionError("The call completed without an error");
        }

        private <R> R await(Function<List<T>, R> mapper) {
            try {
                return mapper.apply(done.get(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                throw new AssertionError("The call failed: " + e, e);
            }
        }
    }
}

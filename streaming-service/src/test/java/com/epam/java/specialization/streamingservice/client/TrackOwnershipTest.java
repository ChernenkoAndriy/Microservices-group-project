package com.epam.java.specialization.streamingservice.client;

import com.epam.java.specialization.grpc.catalog.v1.TrackInfo;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackOwnershipTest {

    private final CatalogGrpcClient catalog = mock(CatalogGrpcClient.class);
    private final TrackOwnership ownership = new TrackOwnership(catalog);

    @Test
    void ownerPasses() {
        when(catalog.getTrackInfo(1)).thenReturn(TrackInfo.newBuilder().setId(1).setArtistId(7).build());

        assertThatCode(() -> ownership.requireOwner(1L, 7L)).doesNotThrowAnyException();
    }

    @Test
    void someoneElseIsForbidden() {
        when(catalog.getTrackInfo(1)).thenReturn(TrackInfo.newBuilder().setId(1).setArtistId(7).build());

        assertHttpStatus(() -> ownership.requireOwner(1L, 8L), HttpStatus.FORBIDDEN);
    }

    @Test
    void grpcStatusesBecomeHttpStatuses() {
        when(catalog.getTrackInfo(2)).thenThrow(Status.NOT_FOUND.asRuntimeException());
        when(catalog.getTrackInfo(3)).thenThrow(Status.INVALID_ARGUMENT.withDescription("bad id").asRuntimeException());
        when(catalog.getTrackInfo(4)).thenThrow(Status.UNAVAILABLE.asRuntimeException());
        when(catalog.getTrackInfo(5)).thenThrow(Status.DEADLINE_EXCEEDED.asRuntimeException());

        assertHttpStatus(() -> ownership.requireOwner(2L, 7L), HttpStatus.NOT_FOUND);
        assertHttpStatus(() -> ownership.requireOwner(3L, 7L), HttpStatus.BAD_REQUEST);
        assertHttpStatus(() -> ownership.requireOwner(4L, 7L), HttpStatus.SERVICE_UNAVAILABLE);
        assertHttpStatus(() -> ownership.requireOwner(5L, 7L), HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static void assertHttpStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode()).isEqualTo(expected));
    }
}

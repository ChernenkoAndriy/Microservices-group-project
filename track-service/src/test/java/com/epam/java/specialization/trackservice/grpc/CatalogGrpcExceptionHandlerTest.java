package com.epam.java.specialization.trackservice.grpc;

import com.epam.java.specialization.trackservice.exception.CatalogException;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogGrpcExceptionHandlerTest {

    private final CatalogGrpcExceptionHandler handler = new CatalogGrpcExceptionHandler();

    @Test
    void mapsCatalogExceptionsByHttpStatus() {
        assertThat(code(CatalogException.notFound("TRACK_NOT_FOUND", "no"))).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(code(CatalogException.badRequest("INVALID", "no"))).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(code(CatalogException.forbidden("no"))).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(code(CatalogException.unprocessable("X", "no"))).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(code(CatalogException.serviceUnavailable("X", "no"))).isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    void keepsTheDescriptionOfCatalogExceptions() {
        assertThat(handler.handleException(CatalogException.notFound("TRACK_NOT_FOUND", "Track 5 not found"))
                .getStatus().getDescription()).isEqualTo("Track 5 not found");
    }

    @Test
    void temporaryDatabaseFailuresAreUnavailableSoClientsCanRetry() {
        assertThat(code(new CannotCreateTransactionException("no connection"))).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(code(new DataAccessResourceFailureException("db down"))).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(code(new QueryTimeoutException("slow"))).isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    void unexpectedExceptionsAreInternalWithoutDetails() {
        Status status = handler.handleException(new NullPointerException("secret detail")).getStatus();

        assertThat(status.getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(status.getDescription()).doesNotContain("secret detail");
    }

    @Test
    void leavesGrpcStatusesToTheDefaultHandling() {
        assertThat(handler.handleException(Status.ABORTED.asRuntimeException())).isNull();
        assertThat(handler.handleException(Status.ABORTED.asException())).isNull();
    }

    private Status.Code code(Throwable exception) {
        return handler.handleException(exception).getStatus().getCode();
    }
}

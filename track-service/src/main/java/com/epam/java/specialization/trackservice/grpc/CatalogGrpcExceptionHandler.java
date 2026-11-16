package com.epam.java.specialization.trackservice.grpc;

import com.epam.java.specialization.trackservice.exception.ApiException;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.stereotype.Component;

/**
 * The gRPC counterpart of GlobalExceptionHandler: maps an exception thrown by {@link CatalogGrpcService}
 * to a gRPC status. Spring gRPC calls it for exceptions thrown while handling any call.
 */
@Slf4j
@Component
public class CatalogGrpcExceptionHandler implements GrpcExceptionHandler {

    @Override
    public StatusException handleException(Throwable exception) {
        if (exception instanceof StatusException || exception instanceof StatusRuntimeException) {
            return null; // already a gRPC status: the default handling keeps it
        }
        if (exception instanceof ApiException e) {
            log.warn("{}: {}", e.getCode(), e.getMessage());
            return toStatus(e.getStatus().value()).withDescription(e.getMessage()).asException();
        }
        if (exception instanceof IllegalArgumentException) {
            log.warn("Invalid gRPC request: {}", exception.getMessage());
            return Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).asException();
        }
        log.error("Unexpected error in a gRPC call", exception);
        return Status.INTERNAL.withDescription("An unexpected error occurred.").asException();
    }

    /** HTTP status of an {@link ApiException} -> the closest gRPC status code. */
    private static Status toStatus(int httpStatus) {
        return switch (httpStatus) {
            case 400 -> Status.INVALID_ARGUMENT;
            case 401 -> Status.UNAUTHENTICATED;
            case 403 -> Status.PERMISSION_DENIED;
            case 404 -> Status.NOT_FOUND;
            case 409 -> Status.ALREADY_EXISTS;
            case 422 -> Status.FAILED_PRECONDITION;
            case 503 -> Status.UNAVAILABLE;
            default -> Status.INTERNAL;
        };
    }
}

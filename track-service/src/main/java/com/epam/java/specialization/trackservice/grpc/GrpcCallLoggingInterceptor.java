package com.epam.java.specialization.trackservice.grpc;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logs every gRPC call once it ends: method, status code, duration and attempt number.
 * <p>
 * A client that retries a call (gRPC retry policy) sends {@code grpc-previous-rpc-attempts} on every attempt after
 * the first, so "attempt 2" and "attempt 3" lines show the retries. It is the first (outermost) interceptor, so it
 * also sees the status set by the exception handler and by {@link InternalTokenServerInterceptor}.
 */
@Slf4j
@Component
@GlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GrpcCallLoggingInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> PREVIOUS_ATTEMPTS =
            Metadata.Key.of("grpc-previous-rpc-attempts", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        int attempt = attemptNumber(headers.get(PREVIOUS_ATTEMPTS));
        long start = System.nanoTime();
        AtomicBoolean logged = new AtomicBoolean();

        ServerCall<ReqT, RespT> loggingCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                if (logged.compareAndSet(false, true)) {
                    logCall(method, status.getCode(), status.getDescription(), attempt, start);
                }
                super.close(status, trailers);
            }
        };
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(next.startCall(loggingCall, headers)) {
            @Override
            public void onCancel() {
                // The client cancelled or its deadline expired before the server closed the call.
                if (logged.compareAndSet(false, true)) {
                    logCall(method, Status.Code.CANCELLED, "cancelled by the client", attempt, start);
                }
                super.onCancel();
            }
        };
    }

    private static void logCall(String method, Status.Code code, String description, int attempt, long start) {
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        if (code == Status.Code.OK) {
            log.info("gRPC {} -> {} in {} ms (attempt {})", method, code, millis, attempt);
        } else {
            log.warn("gRPC {} -> {} in {} ms (attempt {}): {}", method, code, millis, attempt,
                    Objects.requireNonNullElse(description, "-"));
        }
    }

    private static int attemptNumber(String previousAttempts) {
        if (previousAttempts == null) {
            return 1;
        }
        try {
            return Integer.parseInt(previousAttempts.trim()) + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}

package com.epam.java.specialization.streamingservice.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientStreamTracer;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Logs the result of every gRPC call made by streaming-service.
 * <p>
 * Retries happen inside the channel, below the interceptors, so the call itself is logged once with its final
 * status. Every attempt is a separate stream, though: the stream tracer logs each failed attempt, so a retried
 * call shows "attempt 1 -> UNAVAILABLE", "attempt 2 -> ..." before the final line.
 */
@Component
@GlobalClientInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GrpcClientLoggingInterceptor implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcClientLoggingInterceptor.class);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions, Channel next) {
        String name = method.getFullMethodName();
        long start = System.nanoTime();
        CallOptions traced = callOptions.withStreamTracerFactory(attemptLogger(name));
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, traced)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                        if (status.isOk()) {
                            log.info("gRPC call {} -> OK in {} ms", name, millis);
                        } else {
                            log.warn("gRPC call {} -> {} in {} ms: {}", name, status.getCode(), millis,
                                    Objects.requireNonNullElse(status.getDescription(), "-"));
                        }
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }

    private static ClientStreamTracer.Factory attemptLogger(String name) {
        return new ClientStreamTracer.Factory() {
            @Override
            public ClientStreamTracer newClientStreamTracer(ClientStreamTracer.StreamInfo info, Metadata headers) {
                int attempt = info.getPreviousAttempts() + 1;
                return new ClientStreamTracer() {
                    @Override
                    public void streamClosed(Status status) {
                        if (!status.isOk()) {
                            log.info("gRPC call {} attempt {} -> {}", name, attempt, status.getCode());
                        }
                    }
                };
            }
        };
    }
}

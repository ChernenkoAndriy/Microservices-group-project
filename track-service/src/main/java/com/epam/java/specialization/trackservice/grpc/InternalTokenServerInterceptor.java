package com.epam.java.specialization.trackservice.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The gRPC counterpart of InternalTokenFilter: a call must carry the shared service token in the
 * {@code x-internal-token} metadata header. Attached only to {@link CatalogGrpcService}, so the reflection and
 * health services stay open for grpcurl, Postman and probes.
 */
@Slf4j
@Component
public class InternalTokenServerInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> INTERNAL_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final byte[] expectedToken;

    public InternalTokenServerInterceptor(@Value("${internal-api.token}") String token) {
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String token = headers.get(INTERNAL_TOKEN);
        boolean valid = token != null && expectedToken.length > 0
                && MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8));
        if (valid) {
            return next.startCall(call, headers);
        }
        log.warn("Rejected gRPC call to {} without a valid {}", call.getMethodDescriptor().getFullMethodName(),
                INTERNAL_TOKEN.name());
        call.close(Status.UNAUTHENTICATED.withDescription("The x-internal-token metadata is missing or invalid"),
                new Metadata());
        return new ServerCall.Listener<>() {
        };
    }
}

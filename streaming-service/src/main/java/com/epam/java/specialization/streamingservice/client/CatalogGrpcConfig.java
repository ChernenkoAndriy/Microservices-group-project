package com.epam.java.specialization.streamingservice.client;

import com.epam.java.specialization.grpc.catalog.v1.CatalogInternalServiceGrpc.CatalogInternalServiceBlockingStub;
import com.epam.java.specialization.grpc.catalog.v1.CatalogInternalServiceGrpc.CatalogInternalServiceStub;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * Stubs of catalog-service's gRPC API (catalog_internal.proto), registered as beans on the channel "catalog"
 * ({@code spring.grpc.client.channel.catalog} in application.yaml: target, deadline, load balancing, retries).
 * The blocking stub serves unary and server-streaming calls; client and bidirectional streaming need the async stub.
 */
@Configuration
@ImportGrpcClients(target = "catalog",
        types = {CatalogInternalServiceBlockingStub.class, CatalogInternalServiceStub.class})
public class CatalogGrpcConfig {
}

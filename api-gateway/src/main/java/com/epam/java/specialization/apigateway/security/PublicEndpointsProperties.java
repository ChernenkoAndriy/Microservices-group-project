package com.epam.java.specialization.apigateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gateway.security")
public record PublicEndpointsProperties(List<PublicEndpoint> publicEndpoints) {

    public PublicEndpointsProperties {
        publicEndpoints = publicEndpoints == null ? List.of() : List.copyOf(publicEndpoints);
    }

    public record PublicEndpoint(String method, List<String> paths) {
    }
}

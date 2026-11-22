package com.epam.java.specialization.libraryservice.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/** Internal APIs of catalog-service and auth-service, both protected by the shared {@code X-Internal-Token}. */
@ConfigurationProperties("clients")
public record ClientProperties(
        URI catalogBaseUrl,
        URI authBaseUrl,
        String internalToken,
        Duration connectTimeout,
        Duration readTimeout) {
}

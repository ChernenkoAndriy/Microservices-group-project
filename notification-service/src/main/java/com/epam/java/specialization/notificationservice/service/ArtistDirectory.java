package com.epam.java.specialization.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;

/** Looks up an artist's name in catalog-service (public REST endpoint), for the release email. */
@Component
public class ArtistDirectory {

    private static final Logger log = LoggerFactory.getLogger(ArtistDirectory.class);
    private static final String FALLBACK = "An artist you follow";

    private final RestClient client;

    public ArtistDirectory(@Value("${notification.catalog-url:http://catalog-service:8082}") String catalogUrl) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        this.client = RestClient.builder().baseUrl(catalogUrl).requestFactory(factory).build();
    }

    public String nameOf(Long artistId) {
        try {
            JsonNode node = client.get().uri("/api/v1/artists/{id}", artistId).retrieve().body(JsonNode.class);
            return node != null && node.hasNonNull("name") ? node.get("name").asString() : FALLBACK;
        } catch (Exception e) {
            log.warn("Could not look up artist {}: {}", artistId, e.getMessage());
            return FALLBACK;
        }
    }
}

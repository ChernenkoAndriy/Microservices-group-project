package com.epam.java.specialization.notificationservice.service;

import com.epam.java.specialization.notificationservice.client.CatalogArtistsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/** Looks up an artist's name in catalog-service, for the release email; a neutral name when catalog is down. */
@Component
public class ArtistDirectory {

    private static final Logger log = LoggerFactory.getLogger(ArtistDirectory.class);
    private static final String FALLBACK = "An artist you follow";

    private final CatalogArtistsClient catalog;

    public ArtistDirectory(CatalogArtistsClient catalog) {
        this.catalog = catalog;
    }

    public String nameOf(Long artistId) {
        try {
            JsonNode node = catalog.findArtist(artistId);
            return node != null && node.hasNonNull("name") ? node.get("name").asString() : FALLBACK;
        } catch (RestClientException e) {
            log.warn("Could not look up artist {}: {}", artistId, e.getMessage());
            return FALLBACK;
        }
    }
}

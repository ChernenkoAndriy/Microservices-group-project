package com.epam.java.specialization.notificationservice.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import tools.jackson.databind.JsonNode;

/** Declarative client of catalog-service's public artist endpoint (implemented by HttpServiceProxyFactory). */
@HttpExchange("/api/v1/artists")
public interface CatalogArtistsClient {

    @GetExchange("/{artistId}")
    JsonNode findArtist(@PathVariable Long artistId);
}

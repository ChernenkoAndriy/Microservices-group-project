package com.epam.java.specialization.notificationservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class CatalogClientConfig {

    @Bean
    public CatalogArtistsClient catalogArtistsClient(RestClient.Builder builder,
                                                     @Value("${notification.catalog-url:http://catalog-service:8082}")
                                                     String catalogUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        RestClient restClient = builder.baseUrl(catalogUrl).requestFactory(requestFactory).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build()
                .createClient(CatalogArtistsClient.class);
    }
}

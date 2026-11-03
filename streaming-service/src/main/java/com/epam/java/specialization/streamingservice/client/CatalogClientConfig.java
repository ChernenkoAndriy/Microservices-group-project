package com.epam.java.specialization.streamingservice.client;

import com.epam.java.specialization.streamingservice.client.catalog.api.InternalTracksApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;

/**
 * Client of catalog-service's internal API. It is built from the auto-configured {@link RestClient.Builder},
 * which is observed: every call gets a CLIENT span and sends {@code traceparent}, so catalog-service continues
 * the upload's trace. {@code RestClient.create()} would skip that.
 */
@Configuration
@EnableConfigurationProperties(CatalogClientProperties.class)
public class CatalogClientConfig {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Bean
    public InternalTracksApi catalogTracksApi(RestClient.Builder builder, CatalogClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = builder
                .baseUrl(properties.baseUrl().toString())
                .defaultHeader(INTERNAL_TOKEN_HEADER, properties.internalToken())
                .requestFactory(requestFactory)
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build()
                .createClient(InternalTracksApi.class);
    }
}

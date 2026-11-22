package com.epam.java.specialization.libraryservice.client;

import com.epam.java.specialization.libraryservice.client.auth.api.InternalUsersApi;
import com.epam.java.specialization.libraryservice.client.catalog.api.InternalTracksApi;
import com.epam.java.specialization.libraryservice.filter.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.URI;
import java.net.http.HttpClient;

/** HTTP interface clients generated from the internal contracts (same setup as catalog-service's auth client). */
@Configuration
@EnableConfigurationProperties(ClientProperties.class)
public class ClientConfig {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Bean
    public InternalTracksApi catalogTracksApi(RestClient.Builder builder, ClientProperties properties) {
        return client(builder, properties, properties.catalogBaseUrl(), InternalTracksApi.class);
    }

    @Bean
    public InternalUsersApi authUsersApi(RestClient.Builder builder, ClientProperties properties) {
        return client(builder, properties, properties.authBaseUrl(), InternalUsersApi.class);
    }

    private static <T> T client(RestClient.Builder builder, ClientProperties properties, URI baseUrl, Class<T> type) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = builder.clone()
                .baseUrl(baseUrl.toString())
                .defaultHeader(INTERNAL_TOKEN_HEADER, properties.internalToken())
                .requestInterceptor(correlationIdPropagation())
                .requestFactory(requestFactory)
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build().createClient(type);
    }

    private static ClientHttpRequestInterceptor correlationIdPropagation() {
        return (request, body, execution) -> {
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
            if (correlationId != null) {
                request.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
            }
            return execution.execute(request, body);
        };
    }
}

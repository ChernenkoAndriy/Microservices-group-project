package com.epam.java.specialization.trackservice.client;

import com.epam.java.specialization.trackservice.client.auth.api.InternalUsersApi;
import com.epam.java.specialization.trackservice.filter.CorrelationIdFilter;
import com.epam.java.specialization.trackservice.filter.InternalTokenFilter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(AuthClientProperties.class)
public class AuthClientConfig {

    public static final String AUTH_SERVICE = "auth-service";

    @Bean
    public InternalUsersApi authUsersApi(RestClient.Builder builder, AuthClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = builder
                .baseUrl(properties.baseUrl().toString())
                .defaultHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER, properties.internalToken())
                .requestInterceptor(correlationIdPropagation())
                .requestFactory(requestFactory)
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build()
                .createClient(InternalUsersApi.class);
    }

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerTransitionLogger() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                CircuitBreaker circuitBreaker = event.getAddedEntry();
                circuitBreaker.getEventPublisher().onStateTransition(transition ->
                        log.warn("Circuit breaker {}: {}", circuitBreaker.getName(), transition.getStateTransition()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event) {
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event) {
            }
        };
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

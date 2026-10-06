package com.epam.java.specialization.trackservice.client;

import com.epam.java.specialization.trackservice.client.auth.api.InternalUsersApi;
import com.epam.java.specialization.trackservice.filter.CorrelationIdFilter;
import com.epam.java.specialization.trackservice.filter.InternalTokenFilter;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
    public CircuitBreaker authCircuitBreaker(AuthClientProperties properties) {
        AuthClientProperties.CircuitBreakerSettings settings = properties.circuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumNumberOfCalls())
                .failureRateThreshold(settings.failureRateThreshold())
                .waitDurationInOpenState(settings.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(settings.permittedCallsInHalfOpenState())
                .recordException(AuthClientConfig::isTransientFailure)
                .ignoreExceptions(BulkheadFullException.class)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of(AUTH_SERVICE, config);
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("Circuit breaker {}: {}", AUTH_SERVICE, event.getStateTransition()));
        return circuitBreaker;
    }

    @Bean
    public Retry authRetry(AuthClientProperties properties) {
        AuthClientProperties.RetrySettings settings = properties.retry();
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(settings.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        settings.initialInterval(), settings.multiplier(), settings.randomizationFactor()))
                .retryOnException(AuthClientConfig::isTransientFailure)
                .build();
        return Retry.of(AUTH_SERVICE, config);
    }

    @Bean
    public Bulkhead authBulkhead(AuthClientProperties properties) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(properties.bulkhead().maxConcurrentCalls())
                .maxWaitDuration(properties.bulkhead().maxWaitDuration())
                .build();
        return Bulkhead.of(AUTH_SERVICE, config);
    }

    static boolean isTransientFailure(Throwable e) {
        return e instanceof HttpServerErrorException || e instanceof ResourceAccessException;
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

package com.epam.java.specialization.trackservice.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("auth-client")
public record AuthClientProperties(
        URI baseUrl,
        String internalToken,
        Duration connectTimeout,
        Duration readTimeout,
        CircuitBreakerSettings circuitBreaker,
        RetrySettings retry,
        BulkheadSettings bulkhead) {

    public record CircuitBreakerSettings(
            int slidingWindowSize,
            int minimumNumberOfCalls,
            float failureRateThreshold,
            Duration waitDurationInOpenState,
            int permittedCallsInHalfOpenState) {
    }

    public record RetrySettings(
            int maxAttempts,
            Duration initialInterval,
            double multiplier,
            double randomizationFactor) {
    }

    public record BulkheadSettings(int maxConcurrentCalls, Duration maxWaitDuration) {
    }
}

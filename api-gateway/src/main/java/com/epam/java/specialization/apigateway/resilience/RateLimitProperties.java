package com.epam.java.specialization.apigateway.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param capacity        the burst a single client may send at once
 * @param refillPerSecond the sustained number of requests per second a single client may send
 */
@ConfigurationProperties(prefix = "gateway.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled,
                                  @DefaultValue("50") int capacity,
                                  @DefaultValue("20") double refillPerSecond) {
}

package com.epam.java.specialization.authservice.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings bound from the {@code jwt.*} properties.
 *
 * @param secret     Base64-encoded HMAC-SHA signing key (at least 256 bits)
 * @param ttl        lifetime of an issued access token
 * @param refreshTtl lifetime of an issued refresh token
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration ttl, Duration refreshTtl) {
}

package com.epam.java.specialization.authservice.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration ttl, Duration refreshTtl) {
}

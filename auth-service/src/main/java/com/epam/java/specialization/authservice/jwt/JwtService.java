package com.epam.java.specialization.authservice.jwt;

import com.epam.java.specialization.authservice.exception.TokenIsNotValidException;
import com.epam.java.specialization.authservice.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Service
public class JwtService {

    private static final String EMAIL_CLAIM = "email";
    private static final String USERNAME_CLAIM = "username";
    private static final String ROLE_CLAIM = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(EMAIL_CLAIM, user.getEmail())
                .claim(USERNAME_CLAIM, user.getUsername())
                .claim(ROLE_CLAIM, user.getRole() != null ? user.getRole().name() : null)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.ttl())))
                .signWith(signingKey)
                .compact();
        log.debug("Generated JWT for user id={} valid for {}", user.getId(), properties.ttl());
        return token;
    }

    public long getTtlSeconds() {
        return properties.ttl().toSeconds();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (TokenIsNotValidException e) {
            return false;
        }
    }

    /**
     * @throws TokenIsNotValidException if the token is malformed, has a bad signature, is expired
     *                                  or does not carry a numeric user id as its subject
     */
    public Long extractUserId(String token) {
        String subject = parseClaims(token).getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            log.warn("JWT subject {} is not a user id", subject);
            throw new TokenIsNotValidException("Token subject is not a valid user id");
        }
    }

    private Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenIsNotValidException("Token is missing");
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            throw new TokenIsNotValidException("Token is not valid: " + e.getMessage());
        }
    }
}

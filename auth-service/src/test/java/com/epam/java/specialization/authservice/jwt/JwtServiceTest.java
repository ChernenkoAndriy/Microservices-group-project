package com.epam.java.specialization.authservice.jwt;

import com.epam.java.specialization.authservice.exception.TokenIsNotValidException;
import com.epam.java.specialization.authservice.model.Role;
import com.epam.java.specialization.authservice.model.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "c3BvdHR5LWF1dGgtc2VydmljZS1kZXYtc2VjcmV0LWtleS1jaGFuZ2UtbWUtcGxlYXNl";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(10), Duration.ofDays(30)));

    @Test
    void generatedTokenIsValidAndCarriesUserId() {
        String token = jwtService.generateToken(user(42L));

        assertTrue(jwtService.isTokenValid(token));
        assertEquals(42L, jwtService.extractUserId(token));
        assertEquals(600, jwtService.getTtlSeconds());
    }

    @Test
    void expiredTokenIsNotValid() {
        JwtService expiring = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-1), Duration.ofDays(30)));
        String token = expiring.generateToken(user(1L));

        assertFalse(jwtService.isTokenValid(token));
        assertThrows(TokenIsNotValidException.class, () -> jwtService.extractUserId(token));
    }

    @Test
    void garbageOrMissingTokenIsNotValid() {
        assertFalse(jwtService.isTokenValid("not-a-jwt"));
        assertFalse(jwtService.isTokenValid(null));
        assertThrows(TokenIsNotValidException.class, () -> jwtService.extractUserId(""));
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("john");
        user.setEmail("john@example.com");
        user.setRole(Role.LISTENER);
        return user;
    }
}

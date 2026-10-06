package com.epam.java.specialization.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

@Component
public class JwtVerifier {

    private static final String ROLE_CLAIM = "role";

    private final JwtParser parser;

    public JwtVerifier(JwtProperties properties) {
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret())))
                .build();
    }

    /**
     * @throws InvalidTokenException if the token is malformed, has a bad signature, is expired
     *                               or does not carry a numeric user id as its subject
     */
    public AuthenticatedUser verify(String token) {
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Token is not valid: " + e.getMessage());
        }

        long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidTokenException("Token subject is not a valid user id");
        }
        return new AuthenticatedUser(userId, claims.get(ROLE_CLAIM, String.class));
    }
}

package com.epam.java.specialization.authservice.dto;

/**
 * @param expiresIn        access token lifetime in seconds
 * @param refreshExpiresIn refresh token lifetime in seconds
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn
) {
    public static TokenResponse bearer(String accessToken, long expiresIn, String refreshToken, long refreshExpiresIn) {
        return new TokenResponse(accessToken, "Bearer", expiresIn, refreshToken, refreshExpiresIn);
    }
}

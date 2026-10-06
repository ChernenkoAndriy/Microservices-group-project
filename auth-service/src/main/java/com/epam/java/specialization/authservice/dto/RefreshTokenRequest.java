package com.epam.java.specialization.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of both {@code /refresh} and {@code /logout}.
 */
public record RefreshTokenRequest(@NotBlank @Size(max = 4096) String refreshToken) {
    @Override
    public String toString() {
        return "RefreshTokenRequest[***]";
    }
}

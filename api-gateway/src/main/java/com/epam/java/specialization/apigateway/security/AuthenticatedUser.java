package com.epam.java.specialization.apigateway.security;

/**
 * The caller identified by a valid access token.
 *
 * @param userId the token subject
 * @param role   the {@code role} claim, or {@code null} if the token has none
 */
public record AuthenticatedUser(long userId, String role) {
}

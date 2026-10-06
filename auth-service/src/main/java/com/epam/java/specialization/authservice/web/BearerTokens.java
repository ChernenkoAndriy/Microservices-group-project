package com.epam.java.specialization.authservice.web;

public final class BearerTokens {

    private static final String BEARER_PREFIX = "Bearer ";

    private BearerTokens() {
    }

    public static String extract(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return authorization.trim();
    }
}

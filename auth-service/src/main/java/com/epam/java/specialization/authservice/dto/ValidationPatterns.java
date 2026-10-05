package com.epam.java.specialization.authservice.dto;

final class ValidationPatterns {

    /** Names may not start or end with whitespace. */
    static final String NO_SURROUNDING_WHITESPACE = "^\\S(?:.*\\S)?$";

    private ValidationPatterns() {
    }
}

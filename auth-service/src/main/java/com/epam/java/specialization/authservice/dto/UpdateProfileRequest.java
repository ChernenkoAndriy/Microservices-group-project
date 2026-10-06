package com.epam.java.specialization.authservice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Partial update: omitted (null) fields keep their value; at least one field is required.
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = ValidationPatterns.NO_SURROUNDING_WHITESPACE) String username,
        @Size(max = 2048) @URL String avatarUrl
) {
    public boolean isEmpty() {
        return username == null && avatarUrl == null;
    }
}

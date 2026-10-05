package com.epam.java.specialization.authservice.dto;

import com.epam.java.specialization.authservice.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code role} may be LISTENER or ARTIST; ADMIN cannot be self-assigned (checked in the service).
 */
public record RegisterRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = ValidationPatterns.NO_SURROUNDING_WHITESPACE) String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull Role role,
        @NotBlank @Size(min = 8, max = 128) String password
) {
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", email=" + email + ", role=" + role + "]";
    }
}

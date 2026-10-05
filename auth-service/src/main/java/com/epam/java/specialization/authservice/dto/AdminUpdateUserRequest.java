package com.epam.java.specialization.authservice.dto;

import com.epam.java.specialization.authservice.model.Role;
import com.epam.java.specialization.authservice.model.UserStatus;

/**
 * Partial update: omitted (null) fields keep their value; at least one field is required.
 */
public record AdminUpdateUserRequest(Role role, UserStatus status) {
    public boolean isEmpty() {
        return role == null && status == null;
    }
}

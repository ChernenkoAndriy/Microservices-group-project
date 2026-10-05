package com.epam.java.specialization.authservice.dto;

import com.epam.java.specialization.authservice.model.Role;
import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.model.UserStatus;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String username,
        Role role,
        UserStatus status,
        String avatarUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getStatus() != null ? user.getStatus() : UserStatus.ACTIVE,
                user.getAvatarUrl(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}

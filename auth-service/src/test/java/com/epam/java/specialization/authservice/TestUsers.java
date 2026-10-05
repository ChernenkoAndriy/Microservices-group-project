package com.epam.java.specialization.authservice;

import com.epam.java.specialization.authservice.model.Role;
import com.epam.java.specialization.authservice.model.User;
import com.epam.java.specialization.authservice.model.UserStatus;

/**
 * Builds {@link User} fixtures for tests.
 */
public final class TestUsers {

    private TestUsers() {
    }

    public static User user(Long id, Role role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setEmail("user" + id + "@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    public static User listener(Long id) {
        return user(id, Role.LISTENER, UserStatus.ACTIVE);
    }

    public static User admin(Long id) {
        return user(id, Role.ADMIN, UserStatus.ACTIVE);
    }
}

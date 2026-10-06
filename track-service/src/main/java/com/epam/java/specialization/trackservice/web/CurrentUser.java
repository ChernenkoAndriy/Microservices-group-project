package com.epam.java.specialization.trackservice.web;

import com.epam.java.specialization.trackservice.exception.CatalogException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Optional;

@Component
public class CurrentUser {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    public static final String ARTIST = "ARTIST";
    public static final String ADMIN = "ADMIN";

    public Optional<Long> id() {
        String header = request().getHeader(USER_ID_HEADER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(header.trim()));
        } catch (NumberFormatException e) {
            throw CatalogException.unauthorized(USER_ID_HEADER + " header is not a valid user id");
        }
    }

    public Long requireRole(String role) {
        Long id = id().orElseThrow(() -> CatalogException.unauthorized("Authentication is required"));
        if (!hasRole(role)) {
            throw CatalogException.forbidden("The " + role + " role is required");
        }
        return id;
    }

    public boolean hasRole(String role) {
        String header = request().getHeader(USER_ROLES_HEADER);
        return header != null && Arrays.stream(header.split(",")).map(String::trim).anyMatch(role::equalsIgnoreCase);
    }

    private static HttpServletRequest request() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}

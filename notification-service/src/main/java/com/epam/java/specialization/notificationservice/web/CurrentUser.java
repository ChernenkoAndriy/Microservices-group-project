package com.epam.java.specialization.notificationservice.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

/** The caller, as identified by the gateway after it has verified the JWT. */
@Component
public class CurrentUser {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    public Long requireId() {
        String header = request().getHeader(USER_ID_HEADER);
        if (header == null || header.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, USER_ID_HEADER + " header is not a valid user id");
        }
    }

    /** 401 without a caller, 403 when the caller is not an administrator. */
    public void requireAdmin() {
        requireId();
        String roles = request().getHeader(USER_ROLES_HEADER);
        boolean admin = roles != null && Arrays.stream(roles.split("[,\\s]+"))
                .anyMatch(r -> r.equalsIgnoreCase("ADMIN") || r.equalsIgnoreCase("ROLE_ADMIN"));
        if (!admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required");
        }
    }

    private static HttpServletRequest request() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}

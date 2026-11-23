package com.epam.java.specialization.libraryservice.web;

import com.epam.java.specialization.libraryservice.exception.LibraryException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** The caller, as identified by the gateway after it has verified the JWT ({@code X-User-Id}). */
@Component
public class CurrentUser {

    public static final String USER_ID_HEADER = "X-User-Id";

    public Long requireId() {
        String header = request().getHeader(USER_ID_HEADER);
        if (header == null || header.isBlank()) {
            throw LibraryException.unauthorized("Authentication is required");
        }
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            throw LibraryException.unauthorized(USER_ID_HEADER + " header is not a valid user id");
        }
    }

    private static HttpServletRequest request() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}

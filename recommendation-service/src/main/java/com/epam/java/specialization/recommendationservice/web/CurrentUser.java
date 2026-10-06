package com.epam.java.specialization.recommendationservice.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUser {

    public static final String USER_ID_HEADER = "X-User-Id";

    public Long id() {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String header = request.getHeader(USER_ID_HEADER);
        if (header == null || header.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, USER_ID_HEADER + " header is missing");
        }
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, USER_ID_HEADER + " header is not a valid user id");
        }
    }
}

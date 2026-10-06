package com.epam.java.specialization.authservice.web;

import com.epam.java.specialization.authservice.exception.UnauthenticatedException;
import com.epam.java.specialization.authservice.jwt.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class CurrentUser {

    public static final String USER_ID_HEADER = "X-User-Id";

    private final JwtService jwtService;

    public Long id() {
        return resolve(((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest());
    }

    Long resolve(HttpServletRequest request) {
        String userIdHeader = request.getHeader(USER_ID_HEADER);
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                return Long.valueOf(userIdHeader.trim());
            } catch (NumberFormatException e) {
                throw new UnauthenticatedException(USER_ID_HEADER + " header is not a valid user id");
            }
        }

        String token = BearerTokens.extract(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            throw new UnauthenticatedException("The access token is missing");
        }
        return jwtService.extractUserId(token);
    }
}

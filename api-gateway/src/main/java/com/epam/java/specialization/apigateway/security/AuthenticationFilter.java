package com.epam.java.specialization.apigateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;
    private final PublicEndpoints publicEndpoints;

    public AuthenticationFilter(JwtVerifier jwtVerifier, PublicEndpoints publicEndpoints) {
        this.jwtVerifier = jwtVerifier;
        this.publicEndpoints = publicEndpoints;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean isPublic = HttpMethod.OPTIONS.matches(request.getMethod()) || publicEndpoints.matches(request);
        String token = extractToken(request.getHeader(HttpHeaders.AUTHORIZATION));

        AuthenticatedUser user = null;
        if (token != null) {
            try {
                user = jwtVerifier.verify(token);
            } catch (InvalidTokenException e) {
                log.debug("{} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
                if (!isPublic) {
                    writeUnauthorized(response, "TOKEN_IS_NOT_VALID", "The access token is not valid or has expired");
                    return;
                }
            }
        } else if (!isPublic) {
            writeUnauthorized(response, "UNAUTHORIZED", "The access token is missing");
            return;
        }

        filterChain.doFilter(new IdentityHeadersRequestWrapper(request, user), response);
    }

    private static String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            authorization = authorization.substring(BEARER_PREFIX.length());
        }
        String token = authorization.trim();
        return token.isEmpty() ? null : token;
    }

    private static void writeUnauthorized(HttpServletResponse response, String code, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,"detail":"%s","code":"%s"}"""
                .formatted(detail, code));
    }
}

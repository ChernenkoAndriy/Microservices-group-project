package com.epam.java.specialization.authservice.security;

import com.epam.java.specialization.authservice.model.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class GatewayHeadersAuthenticationFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long userId = parseUserId(request.getHeader(USER_ID_HEADER));
        List<SimpleGrantedAuthority> authorities = parseRoles(request.getHeader(USER_ROLES_HEADER));

        // Missing or invalid headers leave the request anonymous; the security rules then reject it if needed.
        if (userId != null && authorities != null) {
            var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private static Long parseUserId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @return the authorities for a comma-separated role list, or {@code null} if it is missing or has an unknown role
     */
    private static List<SimpleGrantedAuthority> parseRoles(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Arrays.stream(header.split(","))
                    .map(String::trim)
                    .map(Role::valueOf)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                    .toList();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

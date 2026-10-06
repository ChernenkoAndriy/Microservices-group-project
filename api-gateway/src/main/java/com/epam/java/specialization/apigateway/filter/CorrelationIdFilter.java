package com.epam.java.specialization.apigateway.filter;


import jakarta.servlet.ServletException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (!isValidUUID(correlationId)) {
            correlationId = java.util.UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);

        try {
            response.setHeader("X-Correlation-Id", correlationId);
            filterChain.doFilter(new CorrelationIdRequestWrapper(request, correlationId), response);
        }
        finally { MDC.remove("correlationId"); }
    }

    private boolean isValidUUID(String correlationId) {
        if (correlationId == null || correlationId.isEmpty()) {
            return false;
        }
        try {
            java.util.UUID.fromString(correlationId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
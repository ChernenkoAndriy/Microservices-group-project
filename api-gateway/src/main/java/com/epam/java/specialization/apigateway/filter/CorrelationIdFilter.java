package com.epam.java.specialization.apigateway.filter;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (!isValidUUID(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        // Set before the chain, so responses the gateway writes itself (401, 429, 503) carry it too.
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(new CorrelationIdRequestWrapper(request, correlationId),
                    new CorrelationIdResponseWrapper(response));
        }
        finally { MDC.remove(CORRELATION_ID_MDC_KEY); }
    }

    private boolean isValidUUID(String correlationId) {
        if (correlationId == null || correlationId.isEmpty()) {
            return false;
        }
        try {
            UUID.fromString(correlationId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Keeps the gateway's correlation id: a downstream service echoes the same header, which the proxy would
     * otherwise add as a second value.
     */
    private static final class CorrelationIdResponseWrapper extends HttpServletResponseWrapper {

        CorrelationIdResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            if (!CORRELATION_ID_HEADER.equalsIgnoreCase(name)) {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (!CORRELATION_ID_HEADER.equalsIgnoreCase(name)) {
                super.addHeader(name, value);
            }
        }
    }
}

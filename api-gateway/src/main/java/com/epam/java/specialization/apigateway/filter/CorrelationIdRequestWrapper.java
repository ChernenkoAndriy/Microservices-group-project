package com.epam.java.specialization.apigateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;

public class CorrelationIdRequestWrapper extends HttpServletRequestWrapper {

    private final String correlationId;
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    public CorrelationIdRequestWrapper(HttpServletRequest request, String correlationId) {
        super(request);
        this.correlationId = correlationId;
    }

    @Override
    public String getHeader(String name) {
        if (CORRELATION_ID_HEADER.equalsIgnoreCase(name)) {
            return this.correlationId;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (CORRELATION_ID_HEADER.equalsIgnoreCase(name)) {
            return Collections.enumeration(Collections.singletonList(this.correlationId));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Enumeration<String> headerNames = super.getHeaderNames();
        java.util.List<String> headerList = Collections.list(headerNames);
        if (!headerList.contains(CORRELATION_ID_HEADER)) {
            headerList.add(CORRELATION_ID_HEADER);
        }
        return Collections.enumeration(headerList);
    }

}

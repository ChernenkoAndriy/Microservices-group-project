package com.epam.java.specialization.apigateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IdentityHeadersRequestWrapper extends HttpServletRequestWrapper {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    private final Map<String, String> identityHeaders = new LinkedHashMap<>();

    public IdentityHeadersRequestWrapper(HttpServletRequest request, AuthenticatedUser user) {
        super(request);
        if (user != null) {
            identityHeaders.put(USER_ID_HEADER, String.valueOf(user.userId()));
            if (user.role() != null) {
                identityHeaders.put(USER_ROLES_HEADER, user.role());
            }
        }
    }

    @Override
    public String getHeader(String name) {
        if (isIdentityHeader(name)) {
            return identityHeader(name);
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (isIdentityHeader(name)) {
            String value = identityHeader(name);
            return Collections.enumeration(value == null ? List.of() : List.of(value));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> headerNames = Collections.list(super.getHeaderNames()).stream()
                .filter(name -> !isIdentityHeader(name))
                .collect(Collectors.toCollection(ArrayList::new));
        headerNames.addAll(identityHeaders.keySet());
        return Collections.enumeration(headerNames);
    }

    private static boolean isIdentityHeader(String name) {
        return USER_ID_HEADER.equalsIgnoreCase(name) || USER_ROLES_HEADER.equalsIgnoreCase(name);
    }

    private String identityHeader(String name) {
        return USER_ID_HEADER.equalsIgnoreCase(name)
                ? identityHeaders.get(USER_ID_HEADER)
                : identityHeaders.get(USER_ROLES_HEADER);
    }
}

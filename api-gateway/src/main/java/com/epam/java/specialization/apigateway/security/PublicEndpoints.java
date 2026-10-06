package com.epam.java.specialization.apigateway.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

@Component
public class PublicEndpoints {

    private final List<Rule> rules;

    public PublicEndpoints(PublicEndpointsProperties properties) {
        this.rules = properties.publicEndpoints().stream()
                .flatMap(endpoint -> endpoint.paths().stream()
                        .map(path -> new Rule(endpoint.method(), PathPatternParser.defaultInstance.parse(path))))
                .toList();
    }

    public boolean matches(HttpServletRequest request) {
        // The servlet path is decoded and normalized ("..", ";params" removed), i.e. the path the target service sees.
        String pathInfo = request.getPathInfo();
        PathContainer path = PathContainer.parsePath(
                request.getServletPath() + (pathInfo == null ? "" : pathInfo));
        return rules.stream().anyMatch(rule -> rule.matches(request.getMethod(), path));
    }

    private record Rule(String method, PathPattern pattern) {
        boolean matches(String requestMethod, PathContainer path) {
            return (method == null || method.equalsIgnoreCase(requestMethod)) && pattern.matches(path);
        }
    }
}

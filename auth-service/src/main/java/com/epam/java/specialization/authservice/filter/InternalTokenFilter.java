package com.epam.java.specialization.authservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    private final byte[] expectedToken;
    private final JsonMapper jsonMapper;

    public InternalTokenFilter(@Value("${internal-api.token}") String token, JsonMapper jsonMapper) {
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = request.getHeader(INTERNAL_TOKEN_HEADER);
        boolean valid = token != null && expectedToken.length > 0
                && MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8));
        if (valid) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rejected internal call to {} without a valid {}", request.getRequestURI(), INTERNAL_TOKEN_HEADER);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "The " + INTERNAL_TOKEN_HEADER + " header is missing or invalid");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INVALID_INTERNAL_TOKEN");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }
}

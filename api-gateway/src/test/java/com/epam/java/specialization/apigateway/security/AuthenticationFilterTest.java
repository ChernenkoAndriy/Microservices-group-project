package com.epam.java.specialization.apigateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationFilterTest {

    private static final String SECRET = "c3BvdHR5LWF1dGgtc2VydmljZS1kZXYtc2VjcmV0LWtleS1jaGFuZ2UtbWUtcGxlYXNl";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));

    private final AuthenticationFilter filter = new AuthenticationFilter(
            new JwtVerifier(new JwtProperties(SECRET)),
            new PublicEndpoints(new PublicEndpointsProperties(List.of(
                    new PublicEndpointsProperties.PublicEndpoint("POST", List.of("/api/v1/auth/login")),
                    new PublicEndpointsProperties.PublicEndpoint("GET", List.of("/api/v1/tracks/{id}"))))));

    @Test
    void protectedEndpointWithoutTokenIsRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", "/api/v1/users/me"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void protectedEndpointWithInvalidTokenIsRejected() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer " + token(7L, "LISTENER", Instant.now().minusSeconds(60)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"TOKEN_IS_NOT_VALID\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void validTokenForwardsIdentityAndDropsClientHeaders() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer " + token(7L, "ARTIST", Instant.now().plusSeconds(600)));
        request.addHeader("X-User-Id", "1");
        request.addHeader("x-user-roles", "ADMIN");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(forwarded.getHeader("X-User-Id")).isEqualTo("7");
        assertThat(forwarded.getHeader("X-User-Roles")).isEqualTo("ARTIST");
        assertThat(Collections.list(forwarded.getHeaders("X-User-Roles"))).containsExactly("ARTIST");
        assertThat(Collections.list(forwarded.getHeaderNames()))
                .filteredOn(name -> name.equalsIgnoreCase("X-User-Id") || name.equalsIgnoreCase("X-User-Roles"))
                .containsExactly("X-User-Id", "X-User-Roles");
    }

    @Test
    void publicEndpointPassesWithoutTokenAndDropsClientHeaders() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/auth/login");
        request.addHeader("X-User-Id", "1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeader("X-User-Id")).isNull();
        assertThat(Collections.list(forwarded.getHeaderNames())).doesNotContain("X-User-Id");
    }

    @Test
    void publicEndpointIgnoresInvalidToken() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/auth/login");
        request.addHeader("Authorization", "Bearer not-a-jwt");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(((HttpServletRequest) chain.getRequest()).getHeader("X-User-Id")).isNull();
    }

    @Test
    void publicEndpointForwardsIdentityOfValidToken() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/tracks/42");
        request.addHeader("Authorization", "Bearer " + token(7L, "LISTENER", Instant.now().plusSeconds(600)));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(((HttpServletRequest) chain.getRequest()).getHeader("X-User-Id")).isEqualTo("7");
    }

    @Test
    void publicPathWithOtherMethodIsProtected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("DELETE", "/api/v1/tracks/42"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void traversalOutOfPublicPathIsMatchedOnNormalizedPath() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/users/me");
        request.setRequestURI("/api/v1/tracks/../users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void corsPreflightPassesWithoutToken() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("OPTIONS", "/api/v1/users/me"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    /** Builds a request the way the servlet container does: {@code servletPath} holds the normalized path. */
    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private static String token(long userId, String role, Instant expiresAt) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(expiresAt.minusSeconds(600)))
                .expiration(Date.from(expiresAt))
                .signWith(KEY)
                .compact();
    }
}

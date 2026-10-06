package com.epam.java.specialization.authservice.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class InternalTokenFilterTest {

    private final InternalTokenFilter filter = new InternalTokenFilter("secret", JsonMapper.builder().build());

    @Test
    void letsInternalCallWithTokenThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/internal/users", "secret"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void rejectsInternalCallWithWrongToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/internal/users", "guess"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_INTERNAL_TOKEN\"");
    }

    @Test
    void rejectsInternalCallWithoutToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/internal/users", null), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void ignoresPublicPaths() throws Exception {
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/users/me", null), new MockHttpServletResponse(), chain);

        assertThat(((MockFilterChain) chain).getRequest()).isNotNull();
    }

    @Test
    void blankConfiguredTokenRejectsEverything() throws Exception {
        InternalTokenFilter blank = new InternalTokenFilter("", JsonMapper.builder().build());
        MockHttpServletResponse response = new MockHttpServletResponse();

        blank.doFilter(request("/internal/users", ""), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private static MockHttpServletRequest request(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (token != null) {
            request.addHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER, token);
        }
        return request;
    }
}

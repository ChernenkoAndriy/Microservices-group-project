package com.epam.java.specialization.apigateway.resilience;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final AtomicLong now = new AtomicLong();
    private final RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(true, 2, 1), now::get);

    @Test
    void allowsBurstThenRejectsWithRetryAfter() throws Exception {
        assertThat(send("7").getStatus()).isEqualTo(200);
        assertThat(send("7").getStatus()).isEqualTo(200);

        MockHttpServletResponse rejected = send("7");

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("1");
        assertThat(rejected.getContentType()).startsWith("application/problem+json");
        assertThat(rejected.getContentAsString()).contains("\"code\":\"RATE_LIMIT_EXCEEDED\"");
    }

    @Test
    void refillsOverTime() throws Exception {
        send("7");
        send("7");
        assertThat(send("7").getStatus()).isEqualTo(429);

        now.addAndGet(TimeUnit.SECONDS.toNanos(1));

        assertThat(send("7").getStatus()).isEqualTo(200);
    }

    @Test
    void limitsEachClientSeparately() throws Exception {
        send("7");
        send("7");
        assertThat(send("7").getStatus()).isEqualTo(429);

        assertThat(send("8").getStatus()).isEqualTo(200);
        assertThat(send(null).getStatus()).isEqualTo(200);
    }

    @Test
    void skipsActuator() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse send(String userId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tracks");
        if (userId != null) {
            request.addHeader("X-User-Id", userId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}

package com.epam.java.specialization.authservice.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> idSeenByChain = new AtomicReference<>();
    private final FilterChain chain = (req, res) -> idSeenByChain.set(MDC.get("correlationId"));

    @Test
    void reusesIncomingCorrelationId() throws Exception {
        request.addHeader("X-Correlation-Id", "abc-123");

        filter.doFilter(request, response, chain);

        assertThat(idSeenByChain.get()).isEqualTo("abc-123");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("abc-123");
    }

    @Test
    void generatesUuidWhenHeaderIsMissing() throws Exception {
        filter.doFilter(request, response, chain);

        String generated = response.getHeader("X-Correlation-Id");
        assertThatCode(() -> UUID.fromString(generated)).doesNotThrowAnyException();
        assertThat(idSeenByChain.get()).isEqualTo(generated);
    }

    @Test
    void generatesUuidWhenHeaderIsBlank() throws Exception {
        request.addHeader("X-Correlation-Id", "  ");

        filter.doFilter(request, response, chain);

        assertThatCode(() -> UUID.fromString(response.getHeader("X-Correlation-Id"))).doesNotThrowAnyException();
    }

    @Test
    void clearsMdcAfterRequestEvenOnFailure() {
        request.addHeader("X-Correlation-Id", "abc-123");
        FilterChain failingChain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("correlationId")).isNull();
    }
}

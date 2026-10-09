package com.epam.java.specialization.apigateway.resilience;

import com.epam.java.specialization.apigateway.security.IdentityHeadersRequestWrapper;
import com.epam.java.specialization.apigateway.web.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Limits how many requests one client may send: an authenticated user by id, an anonymous caller by IP.
 * Runs after {@link com.epam.java.specialization.apigateway.security.AuthenticationFilter}, which sets the user id.
 * The buckets live in this instance's memory, so with N gateway replicas a client gets up to N times the limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final RateLimitProperties properties;
    private final LongSupplier clock;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    RateLimitFilter(RateLimitProperties properties, LongSupplier clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long now = clock.getAsLong();
        if (buckets.size() > CLEANUP_THRESHOLD) {
            buckets.values().removeIf(bucket -> bucket.isFull(now));
        }

        String client = clientKey(request);
        long waitNanos = buckets
                .computeIfAbsent(client, key -> new TokenBucket(properties.capacity(), properties.refillPerSecond(), now))
                .tryConsume(now);
        if (waitNanos == 0) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, (long) Math.ceil((double) waitNanos / TimeUnit.SECONDS.toNanos(1)));
        log.warn("Rate limit exceeded for {} on {} {}", client, request.getMethod(), request.getRequestURI());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        ProblemResponses.write(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                "Too many requests, retry after " + retryAfterSeconds + " s");
    }

    private static String clientKey(HttpServletRequest request) {
        String userId = request.getHeader(IdentityHeadersRequestWrapper.USER_ID_HEADER);
        return userId != null ? "user:" + userId : "ip:" + request.getRemoteAddr();
    }
}

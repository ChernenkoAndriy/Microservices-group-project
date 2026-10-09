package com.epam.java.specialization.apigateway.resilience;

import java.util.concurrent.TimeUnit;

/**
 * A token bucket: holds up to {@code capacity} tokens (the allowed burst) and refills continuously at
 * {@code refillPerSecond}. Each request takes one token.
 */
final class TokenBucket {

    private final double capacity;
    private final double tokensPerNano;
    private double tokens;
    private long lastRefill;

    TokenBucket(int capacity, double refillPerSecond, long now) {
        this.capacity = capacity;
        this.tokensPerNano = refillPerSecond / TimeUnit.SECONDS.toNanos(1);
        this.tokens = capacity;
        this.lastRefill = now;
    }

    /**
     * @return 0 if a token was taken, otherwise the nanoseconds until the next token is available
     */
    synchronized long tryConsume(long now) {
        refill(now);
        if (tokens >= 1) {
            tokens -= 1;
            return 0;
        }
        return (long) Math.ceil((1 - tokens) / tokensPerNano);
    }

    /**
     * A full bucket carries no state worth keeping, so it can be dropped and recreated on the next request.
     */
    synchronized boolean isFull(long now) {
        refill(now);
        return tokens >= capacity;
    }

    private void refill(long now) {
        tokens = Math.min(capacity, tokens + (now - lastRefill) * tokensPerNano);
        lastRefill = now;
    }
}

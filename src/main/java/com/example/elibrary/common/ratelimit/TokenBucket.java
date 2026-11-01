package com.example.elibrary.common.ratelimit;

import java.util.function.LongSupplier;

/**
 * Classic token bucket: holds up to {@code capacity} tokens (the allowed burst) and refills continuously at
 * {@code refillPerSecond}. Each request takes one token; with none left the request is rejected.
 * <p>
 * Starts full, so a user's first few actions are never throttled. Thread-safe; contention is per user only,
 * because every user has their own bucket.
 */
public final class TokenBucket {

    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private final double capacity;
    private final double refillPerNano;
    private final LongSupplier nanoTime;

    private double tokens;
    private long lastRefillNanos;

    public TokenBucket(int capacity, double refillPerSecond, LongSupplier nanoTime) {
        if (capacity < 1 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("capacity must be >= 1 and refill rate > 0");
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / NANOS_PER_SECOND;
        this.nanoTime = nanoTime;
        this.tokens = capacity;
        this.lastRefillNanos = nanoTime.getAsLong();
    }

    public synchronized boolean tryConsume() {
        long now = nanoTime.getAsLong();
        tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * refillPerNano);
        lastRefillNanos = now;
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }
}

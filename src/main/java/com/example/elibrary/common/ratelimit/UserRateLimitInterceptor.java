package com.example.elibrary.common.ratelimit;

import com.example.elibrary.common.error.ErrorCode;
import com.example.elibrary.common.error.ErrorResponse;
import com.example.elibrary.common.web.UserIdHeader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Metrics;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Per-user token bucket for write endpoints (borrow / return), protecting the database from scripted abuse.
 * <p>
 * Limits are per instance: with N instances a user can get N times the configured rate. That is acceptable
 * for a single-node service; a multi-node deployment would move this to the gateway or a Redis-backed
 * limiter (see README, "Evolution").
 */
public class UserRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Bounded and expiring, so idle users do not pile up in memory. {@code get(key, loader)} creates at most
     * one bucket per user even under concurrent requests.
     */
    private final Cache<Long, TokenBucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public UserRateLimitInterceptor(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Optional<Long> userId = UserIdHeader.read(request);
        if (userId.isEmpty()) {
            // let the controller answer 401
            return true;
        }
        TokenBucket bucket = buckets.get(userId.get(),
                id -> new TokenBucket(properties.getCapacity(), properties.getRefillPerSecond(), System::nanoTime));
        if (bucket.tryConsume()) {
            return true;
        }

        Metrics.counter("elibrary.requests.rejected", "code", ErrorCode.TOO_MANY_REQUESTS.name()).increment();
        long retryAfterSeconds = (long) Math.ceil(1 / properties.getRefillPerSecond());
        response.setStatus(ErrorCode.TOO_MANY_REQUESTS.status().value());
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(ErrorCode.TOO_MANY_REQUESTS, "too many requests, please slow down"));
        return false;
    }
}

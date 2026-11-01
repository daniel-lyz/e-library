package com.example.elibrary.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBucketTest {

    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong nanos = new AtomicLong();

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        TokenBucket bucket = new TokenBucket(3, 1, nanos::get);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void refillsAtConfiguredRate() {
        TokenBucket bucket = new TokenBucket(1, 2, nanos::get);
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();

        nanos.addAndGet(SECOND / 4);          // half a token
        assertThat(bucket.tryConsume()).isFalse();

        nanos.addAndGet(SECOND / 4);          // now a full token
        assertThat(bucket.tryConsume()).isTrue();
    }

    @Test
    void neverStoresMoreThanCapacity() {
        TokenBucket bucket = new TokenBucket(2, 1, nanos::get);
        nanos.addAndGet(100 * SECOND);        // long idle period

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new TokenBucket(0, 1, nanos::get)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(1, 0, nanos::get)).isInstanceOf(IllegalArgumentException.class);
    }
}

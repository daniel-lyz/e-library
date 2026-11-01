package com.example.elibrary.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock tests can move forward, e.g. to make a loan overdue.
 */
public class TestClock extends Clock {

    private final AtomicReference<Instant> now = new AtomicReference<>();

    public TestClock() {
        reset();
    }

    public void advance(Duration duration) {
        now.updateAndGet(t -> t.plus(duration));
    }

    public void reset() {
        now.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}

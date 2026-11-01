package com.example.elibrary.common.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;

/**
 * Per-user limits for borrow / return requests, under {@code elibrary.rate-limit.*}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "elibrary.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** burst: how many requests a user can make back to back */
    @Min(1)
    private int capacity = 5;

    /** sustained rate: tokens added back per second */
    @Positive
    private double refillPerSecond = 1;
}

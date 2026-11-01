package com.example.elibrary.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.Duration;

/**
 * Lending rules, configurable under {@code elibrary.loan.*}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "elibrary.loan")
public class LoanPolicy {

    /** how long a loan lasts */
    @NotNull
    private Duration period = Duration.ofDays(14);

    /** how many books one user may have on loan at the same time */
    @Min(1)
    private int maxActiveLoans = 5;

    @AssertTrue(message = "period must be positive")
    public boolean isPeriodPositive() {
        return period != null && !period.isNegative() && !period.isZero();
    }
}

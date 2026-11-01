package com.example.elibrary.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoanTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void openSetsActiveStatusAndDueDate() {
        Loan loan = Loan.open(7L, 42L, NOW, Duration.ofDays(14));

        assertThat(loan.getUserId()).isEqualTo(7L);
        assertThat(loan.getBookId()).isEqualTo(42L);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(loan.getBorrowedAt()).isEqualTo(NOW);
        assertThat(loan.getDueAt()).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"));
        assertThat(loan.getReturnedAt()).isNull();
    }

    @Test
    void overdueOnlyStrictlyAfterDueDate() {
        Loan loan = Loan.open(7L, 42L, NOW, Duration.ofDays(14));

        assertThat(loan.isOverdue(loan.getDueAt())).isFalse();
        assertThat(loan.isOverdue(loan.getDueAt().plusMillis(1))).isTrue();
    }

    @Test
    void returnedLoanIsNeverOverdue() {
        Loan returned = Loan.builder()
                .status(LoanStatus.RETURNED)
                .borrowedAt(NOW)
                .dueAt(NOW.plus(Duration.ofDays(14)))
                .returnedAt(NOW.plus(Duration.ofDays(20)))
                .build();

        assertThat(returned.isOverdue(NOW.plus(Duration.ofDays(30)))).isFalse();
    }
}

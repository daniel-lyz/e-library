package com.example.elibrary.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * One user borrowing one book. Loans are never deleted: returning closes the loan and keeps it as history.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Loan {

    private Long id;
    private long userId;
    private long bookId;
    private LoanStatus status;
    private Instant borrowedAt;
    private Instant dueAt;
    private Instant returnedAt;

    public static Loan open(long userId, long bookId, Instant now, Duration loanPeriod) {
        return Loan.builder()
                .userId(userId)
                .bookId(bookId)
                .status(LoanStatus.ACTIVE)
                .borrowedAt(now)
                .dueAt(now.plus(loanPeriod))
                .build();
    }

    public boolean isOverdue(Instant now) {
        return status == LoanStatus.ACTIVE && now.isAfter(dueAt);
    }
}

package com.example.elibrary.domain;

import lombok.Value;

/**
 * Domain events published after a loan changes. Today they only feed logs and metrics; they are the seam
 * where notifications or a message queue would plug in later (see README, "Evolution").
 */
public final class LoanEvents {

    private LoanEvents() {
    }

    @Value
    public static class BookBorrowed {
        long loanId;
        long userId;
        long bookId;
    }

    @Value
    public static class BookReturned {
        long loanId;
        long userId;
        long bookId;
    }
}

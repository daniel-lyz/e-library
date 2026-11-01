package com.example.elibrary.domain;

/**
 * Persisted states only. "Overdue" is deliberately not a state: it is derived from {@code dueAt}
 * at read time, so it is always accurate and needs no background job to keep it up to date.
 */
public enum LoanStatus {
    ACTIVE,
    RETURNED
}

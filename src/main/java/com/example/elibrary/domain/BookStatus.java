package com.example.elibrary.domain;

public enum BookStatus {
    /** listed and can be borrowed */
    ON_SHELF,
    /** withdrawn (e.g. licence expired): hidden from browsing, no new loans, existing loans unaffected */
    OFF_SHELF
}

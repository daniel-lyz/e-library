package com.example.elibrary.domain;

/**
 * Whitelisted sort orders. Clients pick a name; the SQL fragment is fixed here, so nothing a client sends
 * is ever concatenated into SQL. The trailing id makes paging deterministic.
 */
public enum BookSort {
    TITLE("title ASC, id ASC"),
    NEWEST("created_at DESC, id DESC");

    private final String orderBy;

    BookSort(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getOrderBy() {
        return orderBy;
    }
}

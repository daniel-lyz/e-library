package com.example.elibrary.common;

import lombok.Value;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One page of results. Used as the application-layer result and, after {@link #map}, as the response body.
 */
@Value
public class Page<T> {

    List<T> items;
    int page;
    int size;
    long totalItems;
    long totalPages;

    public static <T> Page<T> of(List<T> items, int page, int size, long totalItems) {
        long totalPages = (totalItems + size - 1) / size;
        return new Page<>(items, page, size, totalItems, totalPages);
    }

    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = items.stream().map(mapper).collect(Collectors.toList());
        return new Page<>(mapped, page, size, totalItems, totalPages);
    }
}

package com.example.elibrary.dto;

import com.example.elibrary.domain.BookFormat;
import com.example.elibrary.domain.BookSort;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Query string of GET /api/v1/books.
 */
@Getter
@Setter
public class BookSearchRequest {

    /**
     * Deepest result reachable by paging (page * size). OFFSET paging cost grows with the offset, so
     * an unbounded page number lets a single request make the database skip arbitrarily many rows.
     * Real readers narrow the search instead of paging this far (same idea as Elasticsearch's
     * max_result_window). See README for the keyset / deferred-join alternatives.
     */
    public static final int MAX_RESULT_WINDOW = 10_000;

    /** matched against title and author, case-insensitive */
    @Size(max = 100)
    private String keyword;

    /** EBOOK or JOURNAL; all formats when absent */
    private BookFormat format;

    @Size(max = 64)
    private String category;

    @NotNull
    private BookSort sort = BookSort.TITLE;

    @Min(1)
    private int page = 1;

    @Min(1)
    @Max(100)
    private int size = 20;

    @Parameter(hidden = true)
    @AssertTrue(message = "page * size must not exceed " + MAX_RESULT_WINDOW + "; narrow the search with keyword or category")
    public boolean isWithinResultWindow() {
        return (long) page * size <= MAX_RESULT_WINDOW;
    }
}

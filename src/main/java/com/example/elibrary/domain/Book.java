package com.example.elibrary.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Anything a reader can borrow from the catalogue: an e-book or one issue of a journal (see {@link BookFormat}).
 * The name follows the brief's vocabulary ("books"); lending rules do not depend on the format.
 * <p>
 * Format-specific fields are nullable here; which ones must be present for each format is enforced by the
 * {@code ck_book_format} constraint in the schema.
 * <p>
 * {@code availableCopies} is a denormalised counter. It is never changed in memory and saved back
 * (that read-modify-write would race); it is changed only by conditional UPDATEs in {@code BookMapper},
 * which are atomic. Invariant: {@code totalCopies = availableCopies + number of ACTIVE loans}.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Book {

    private Long id;
    private BookFormat format;
    private String title;
    /** e-books only */
    private String isbn;
    /** e-books only */
    private String author;
    /** journals only: identifies the journal */
    private String issn;
    /** journals only: identifies this issue, e.g. "Vol. 67, No. 3" */
    private String issue;
    /** journals only */
    private LocalDate publishedOn;
    private String category;
    private String description;
    private int totalCopies;
    private int availableCopies;
    private BookStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Rule checked before trying to take a copy. Whether a copy is actually left is decided
     * atomically by the database, not here.
     */
    public void assertBorrowable() {
        if (status != BookStatus.ON_SHELF) {
            throw new BookNotBorrowableException(id);
        }
    }

    /** Snapshot for display only; may be stale by the time the user clicks "borrow". */
    public boolean hasAvailableCopy() {
        return status == BookStatus.ON_SHELF && availableCopies > 0;
    }
}

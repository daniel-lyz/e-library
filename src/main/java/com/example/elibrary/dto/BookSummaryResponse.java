package com.example.elibrary.dto;

import com.example.elibrary.domain.Book;
import com.example.elibrary.domain.BookFormat;
import lombok.Value;

/**
 * List item: no description, to keep listing payloads small. Format-specific fields that do not apply
 * (author for journals, issue for e-books) are omitted from the JSON.
 */
@Value
public class BookSummaryResponse {

    long id;
    BookFormat format;
    String title;
    /** e-books */
    String author;
    /** journals */
    String issue;
    String category;
    int availableCopies;
    int totalCopies;

    public static BookSummaryResponse from(Book book) {
        return new BookSummaryResponse(book.getId(), book.getFormat(), book.getTitle(), book.getAuthor(),
                book.getIssue(), book.getCategory(), book.getAvailableCopies(), book.getTotalCopies());
    }
}

package com.example.elibrary.dto;

import com.example.elibrary.domain.Book;
import com.example.elibrary.domain.BookFormat;
import com.example.elibrary.domain.BookStatus;
import lombok.Value;

import java.time.LocalDate;

/**
 * Fields that do not apply to the format are omitted from the JSON: e-books carry isbn and author,
 * journal issues carry issn, issue and publishedOn.
 */
@Value
public class BookDetailResponse {

    long id;
    BookFormat format;
    String title;
    String isbn;
    String author;
    String issn;
    String issue;
    LocalDate publishedOn;
    String category;
    String description;
    int availableCopies;
    int totalCopies;
    BookStatus status;
    /** convenience flag for clients; the borrow request itself is the authoritative check */
    boolean available;

    public static BookDetailResponse from(Book book) {
        return new BookDetailResponse(book.getId(), book.getFormat(), book.getTitle(), book.getIsbn(),
                book.getAuthor(), book.getIssn(), book.getIssue(), book.getPublishedOn(), book.getCategory(),
                book.getDescription(), book.getAvailableCopies(), book.getTotalCopies(), book.getStatus(),
                book.hasAvailableCopy());
    }
}

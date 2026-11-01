package com.example.elibrary.mapper;

import com.example.elibrary.domain.BookFormat;
import com.example.elibrary.domain.Loan;
import com.example.elibrary.domain.LoanStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Read model: a loan joined with what identifies the book to a reader, in one query (no N+1 when listing).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanDetails {

    private long id;
    private long userId;
    private long bookId;
    private LoanStatus status;
    private Instant borrowedAt;
    private Instant dueAt;
    private Instant returnedAt;
    private BookFormat bookFormat;
    private String bookTitle;
    /** e-books */
    private String bookAuthor;
    /** journals: two issues of one journal share a title, the issue tells them apart */
    private String bookIssue;

    public Loan toLoan() {
        return Loan.builder()
                .id(id)
                .userId(userId)
                .bookId(bookId)
                .status(status)
                .borrowedAt(borrowedAt)
                .dueAt(dueAt)
                .returnedAt(returnedAt)
                .build();
    }
}

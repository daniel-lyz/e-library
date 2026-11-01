package com.example.elibrary.dto;

import com.example.elibrary.domain.BookFormat;
import com.example.elibrary.domain.LoanStatus;
import com.example.elibrary.service.LoanView;
import lombok.Value;

import java.time.Instant;

@Value
public class LoanResponse {

    long id;
    long bookId;
    BookFormat bookFormat;
    String bookTitle;
    /** e-books only */
    String bookAuthor;
    /** journal issues only */
    String bookIssue;
    LoanStatus status;
    /** true when the loan is still active past its due date */
    boolean overdue;
    Instant borrowedAt;
    Instant dueAt;
    /** absent while the loan is active */
    Instant returnedAt;

    public static LoanResponse from(LoanView view) {
        return new LoanResponse(view.getLoanId(), view.getBookId(), view.getBookFormat(), view.getBookTitle(),
                view.getBookAuthor(), view.getBookIssue(), view.getStatus(), view.isOverdue(),
                view.getBorrowedAt(), view.getDueAt(), view.getReturnedAt());
    }
}

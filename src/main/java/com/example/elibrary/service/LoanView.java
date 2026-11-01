package com.example.elibrary.service;

import com.example.elibrary.domain.BookFormat;
import com.example.elibrary.domain.Loan;
import com.example.elibrary.domain.LoanStatus;
import com.example.elibrary.mapper.LoanDetails;
import lombok.Value;

import java.time.Instant;

/**
 * Application-layer result of loan use cases: the loan plus what a reader needs to recognise the book.
 */
@Value
public class LoanView {

    long loanId;
    long bookId;
    BookFormat bookFormat;
    String bookTitle;
    String bookAuthor;
    String bookIssue;
    LoanStatus status;
    boolean overdue;
    Instant borrowedAt;
    Instant dueAt;
    Instant returnedAt;

    public static LoanView of(LoanDetails details, Instant now) {
        Loan loan = details.toLoan();
        return new LoanView(loan.getId(), loan.getBookId(), details.getBookFormat(), details.getBookTitle(),
                details.getBookAuthor(), details.getBookIssue(), loan.getStatus(), loan.isOverdue(now),
                loan.getBorrowedAt(), loan.getDueAt(), loan.getReturnedAt());
    }
}

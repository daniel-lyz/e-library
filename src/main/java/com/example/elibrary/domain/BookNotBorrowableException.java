package com.example.elibrary.domain;

import com.example.elibrary.common.error.BusinessException;
import com.example.elibrary.common.error.ErrorCode;

public class BookNotBorrowableException extends BusinessException {

    public BookNotBorrowableException(long bookId) {
        super(ErrorCode.BOOK_NOT_BORROWABLE, "book " + bookId + " is no longer offered for lending");
    }
}

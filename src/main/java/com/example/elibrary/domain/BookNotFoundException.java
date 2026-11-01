package com.example.elibrary.domain;

import com.example.elibrary.common.error.BusinessException;
import com.example.elibrary.common.error.ErrorCode;

public class BookNotFoundException extends BusinessException {

    public BookNotFoundException(long bookId) {
        super(ErrorCode.BOOK_NOT_FOUND, "book " + bookId + " not found");
    }
}

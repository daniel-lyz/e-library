package com.example.elibrary.domain;

import com.example.elibrary.common.error.BusinessException;
import com.example.elibrary.common.error.ErrorCode;

public class BookUnavailableException extends BusinessException {

    public BookUnavailableException(long bookId) {
        super(ErrorCode.BOOK_UNAVAILABLE, "no copies of book " + bookId + " are available right now");
    }
}

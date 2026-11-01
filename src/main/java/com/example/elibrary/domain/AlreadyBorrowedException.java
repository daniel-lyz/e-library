package com.example.elibrary.domain;

import com.example.elibrary.common.error.BusinessException;
import com.example.elibrary.common.error.ErrorCode;

public class AlreadyBorrowedException extends BusinessException {

    public AlreadyBorrowedException(long bookId) {
        super(ErrorCode.ALREADY_BORROWED, "you already have book " + bookId + " on loan");
    }
}

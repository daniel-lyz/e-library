package com.example.elibrary.domain;

import com.example.elibrary.common.error.BusinessException;
import com.example.elibrary.common.error.ErrorCode;

public class LoanLimitExceededException extends BusinessException {

    public LoanLimitExceededException(int maxActiveLoans) {
        super(ErrorCode.LOAN_LIMIT_EXCEEDED,
                "you can have at most " + maxActiveLoans + " books on loan; return one first");
    }
}

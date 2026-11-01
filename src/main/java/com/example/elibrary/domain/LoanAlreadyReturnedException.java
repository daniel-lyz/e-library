package com.example.elibrary.domain;

import com.example.elibrary.common.error.BusinessException;
import com.example.elibrary.common.error.ErrorCode;

public class LoanAlreadyReturnedException extends BusinessException {

    public LoanAlreadyReturnedException(long loanId) {
        super(ErrorCode.LOAN_ALREADY_RETURNED, "loan " + loanId + " has already been returned");
    }
}

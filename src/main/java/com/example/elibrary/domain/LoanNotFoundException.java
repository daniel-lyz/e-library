package com.example.elibrary.domain;

import com.example.elibrary.common.error.BusinessException;
import com.example.elibrary.common.error.ErrorCode;

/**
 * Also used when the loan exists but belongs to someone else: answering 404 rather than 403
 * avoids revealing which loan ids exist.
 */
public class LoanNotFoundException extends BusinessException {

    public LoanNotFoundException(long loanId) {
        super(ErrorCode.LOAN_NOT_FOUND, "loan " + loanId + " not found");
    }
}

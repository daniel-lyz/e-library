package com.example.elibrary.common.error;

/**
 * Base class for expected business failures. These are normal outcomes (e.g. "no copies left"),
 * so they are logged at INFO and never alert.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

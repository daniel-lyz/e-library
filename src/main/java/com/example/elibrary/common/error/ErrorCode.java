package com.example.elibrary.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes. The HTTP status says what kind of failure it is,
 * the code says exactly which one, so clients can branch on it.
 */
public enum ErrorCode {

    // generic
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    CONCURRENT_CONFLICT(HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // book
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND),
    BOOK_UNAVAILABLE(HttpStatus.CONFLICT),
    BOOK_NOT_BORROWABLE(HttpStatus.UNPROCESSABLE_ENTITY),

    // loan
    LOAN_NOT_FOUND(HttpStatus.NOT_FOUND),
    ALREADY_BORROWED(HttpStatus.CONFLICT),
    LOAN_LIMIT_EXCEEDED(HttpStatus.CONFLICT),
    LOAN_ALREADY_RETURNED(HttpStatus.CONFLICT);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}

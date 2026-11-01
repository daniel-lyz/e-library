package com.example.elibrary.common.error;

import com.example.elibrary.common.error.ErrorResponse.FieldViolation;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates exceptions into {@link ErrorResponse} with a meaningful HTTP status.
 * Unknown errors are logged with a stack trace but never leak internals to the client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        log.info("request rejected: code={}, message={}", e.getErrorCode(), e.getMessage());
        Metrics.counter("elibrary.requests.rejected", "code", e.getErrorCode().name()).increment();
        return respond(ErrorResponse.of(e.getErrorCode(), e.getMessage()), e.getErrorCode());
    }

    /** {@code @Valid} body and query-object validation (MethodArgumentNotValidException extends BindException). */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException e) {
        List<FieldViolation> violations = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldViolation(f.getField(),
                        f.isBindingFailure() ? "invalid value" : f.getDefaultMessage()))
                .collect(Collectors.toList());
        return invalid("request validation failed", violations);
    }

    /** {@code @Validated} path variables / request params. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException e) {
        List<FieldViolation> violations = e.getConstraintViolations().stream()
                .map(v -> new FieldViolation(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .collect(Collectors.toList());
        return invalid("request validation failed", violations);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return invalid("request validation failed", List.of(new FieldViolation(e.getName(), "invalid value")));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception e) {
        return invalid("malformed request", null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException e) {
        return respond(ErrorResponse.of(ErrorCode.NOT_FOUND, "no such endpoint"), ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED, e.getMessage()), ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return respond(ErrorResponse.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE, e.getMessage()), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * Lock wait timeout / deadlock victim. Correctness is preserved (the transaction rolled back),
     * the client may simply retry.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleLockFailure(PessimisticLockingFailureException e) {
        log.warn("lock conflict, transaction rolled back", e);
        Metrics.counter("elibrary.requests.rejected", "code", ErrorCode.CONCURRENT_CONFLICT.name()).increment();
        return respond(ErrorResponse.of(ErrorCode.CONCURRENT_CONFLICT, "concurrent update conflict, please retry"),
                ErrorCode.CONCURRENT_CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("unexpected error", e);
        return respond(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "internal error"), ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> invalid(String message, List<FieldViolation> violations) {
        return respond(ErrorResponse.of(ErrorCode.INVALID_REQUEST, message, violations), ErrorCode.INVALID_REQUEST);
    }

    private static ResponseEntity<ErrorResponse> respond(ErrorResponse body, ErrorCode code) {
        return ResponseEntity.status(code.status()).body(body);
    }

    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }
}

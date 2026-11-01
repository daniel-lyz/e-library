package com.example.elibrary.common.error;

import com.example.elibrary.common.web.TraceIdFilter;
import lombok.Value;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;

/**
 * Error body returned for every non-2xx response.
 */
@Value
public class ErrorResponse {

    String code;
    String message;
    /** field-level validation problems, only present for INVALID_REQUEST */
    List<FieldViolation> violations;
    String traceId;
    Instant timestamp;

    public static ErrorResponse of(ErrorCode code, String message) {
        return of(code, message, null);
    }

    public static ErrorResponse of(ErrorCode code, String message, List<FieldViolation> violations) {
        return new ErrorResponse(code.name(), message, violations, MDC.get(TraceIdFilter.TRACE_ID), Instant.now());
    }

    @Value
    public static class FieldViolation {
        String field;
        String message;
    }
}

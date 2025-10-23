package com.finlab.accounts.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response format for API errors.
 * Provides consistent structure across all endpoints.
 */
public record ErrorResponse(
    int status,
    String error,
    String message,
    Map<String, String> details,
    Instant timestamp
) {
    public static ErrorResponse of(HttpStatus httpStatus, String message) {
        return new ErrorResponse(
            httpStatus.value(),
            httpStatus.getReasonPhrase(),
            message,
            null,
            Instant.now()
        );
    }

    public static ErrorResponse withDetails(
        HttpStatus httpStatus,
        String message,
        Map<String, String> details
    ) {
        return new ErrorResponse(
            httpStatus.value(),
            httpStatus.getReasonPhrase(),
            message,
            details,
            Instant.now()
        );
    }
}

package com.example.productcatalog.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standard JSON error payload returned by {@link GlobalExceptionHandler}.
 * {@code errors} (field -> message) is only present for validation failures.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> errors
) {
}

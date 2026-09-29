package com.example.productcatalog.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns every error into an {@link ApiError}.
 * <p>
 * Extends {@link ResponseEntityExceptionHandler} so that all standard Spring MVC exceptions (405, 415, 406,
 * 404 for unknown static resources, ...) keep their correct status code; {@link #createResponseEntity} only
 * replaces their body. Anything else falls through to {@link #handleUnexpected} and becomes a 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Object> handleProductNotFound(ProductNotFoundException ex, WebRequest request) {
        return handleExceptionInternal(ex, apiError(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of()),
                new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        return handleExceptionInternal(ex, apiError(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", Map.of()),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return handleExceptionInternal(ex, apiError(status, "Validation failed", errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, apiError(status, "Malformed request body", Map.of()), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = "Invalid value for parameter '" + ex.getPropertyName() + "'";
        return handleExceptionInternal(ex, apiError(status, message, Map.of()), headers, status, request);
    }

    /** Final step for every handler above: wraps Spring's default {@link ProblemDetail} body in an {@link ApiError}. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (!(body instanceof ApiError)) {
            String message = (body instanceof ProblemDetail problem && problem.getDetail() != null)
                    ? problem.getDetail()
                    : reasonPhrase(statusCode);
            body = apiError(statusCode, message, Map.of());
        }
        return new ResponseEntity<>(body, headers, statusCode);
    }

    private static ApiError apiError(HttpStatusCode status, String message, Map<String, String> errors) {
        return new ApiError(Instant.now(), status.value(), message, errors);
    }

    private static String reasonPhrase(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.getReasonPhrase() : "Error";
    }
}

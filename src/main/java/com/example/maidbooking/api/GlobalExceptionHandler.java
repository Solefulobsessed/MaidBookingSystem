package com.example.maidbooking.api;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.maidbooking.common.error.DomainException;
import com.example.maidbooking.common.error.ErrorCode;

/** Translates exceptions into {@link ApiError} responses; services never build HTTP responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomain(DomainException ex) {
        return respond(ex.code(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", Map.of("fields", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return respond(ErrorCode.VALIDATION_FAILED, "Malformed request body", Map.of());
    }

    private ResponseEntity<ApiError> respond(ErrorCode code, String message, Map<String, Object> details) {
        ApiError body = new ApiError(code.name(), message, details, LocalDateTime.now(clock));
        return ResponseEntity.status(code.httpStatus()).body(body);
    }
}

package com.example.aiagent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Environment environment;

    public GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    private boolean isDevProfile() {
        return environment.acceptsProfiles(Profiles.of("dev"));
    }

    private String buildErrorMessage(String correlationId, String detailMessage) {
        if (isDevProfile()) {
            return detailMessage;
        }
        return "Internal server error. Reference: " + correlationId;
    }

    private Map<String, Object> errorBody(String message, String correlationId) {
        return Map.of(
                "error", message,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.badRequest().body(Map.of("error", "Validation failed", "details", errors));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIo(IOException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("IO error [{}]", correlationId, ex);
        String message = buildErrorMessage(correlationId, "IO error: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(message, correlationId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled error [{}]", correlationId, ex);
        String message = buildErrorMessage(correlationId, "Internal error: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(message, correlationId));
    }

    /**
     * Handles {@link ResponseStatusException} (e.g., 401 Unauthorized from MCP auth)
     * by returning the appropriate HTTP status code instead of falling through to
     * the generic 500 handler.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("HTTP {}: {}", ex.getStatusCode().value(), ex.getReason());
        String correlationId = UUID.randomUUID().toString();
        return ResponseEntity.status(ex.getStatusCode())
                .body(errorBody(ex.getReason() != null ? ex.getReason() : ex.getMessage(), correlationId));
    }
}

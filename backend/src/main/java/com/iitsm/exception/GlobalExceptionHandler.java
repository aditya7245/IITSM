package com.iitsm.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IitsmException.class)
    ResponseEntity<ApiError> handleBusiness(IitsmException ex, HttpServletRequest req) {
        HttpStatus s = ex.getStatus();
        return ResponseEntity.status(s).body(ApiError.of(s.value(), s.getReasonPhrase(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        ApiError body = new ApiError(Instant.now(), 400, "Bad Request", "Validation failed", req.getRequestURI(), fields);
        return ResponseEntity.badRequest().body(body);
    }

    /** Malformed JSON, an unknown enum value, or a number where text was sent. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "Bad Request", "Malformed or unreadable request body", req.getRequestURI()));
    }

    /** A value in the URL that has the wrong type, such as /api/incidents/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "Bad Request", "Invalid value for '" + ex.getName() + "'", req.getRequestURI()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "Bad Request", "Missing parameter '" + ex.getParameterName() + "'", req.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleWrongMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return ResponseEntity.status(405).body(ApiError.of(405, "Method Not Allowed", "Method " + ex.getMethod() + " is not supported here", req.getRequestURI()));
    }

    /** A URL that matches nothing. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoSuchUrl(NoResourceFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(404).body(ApiError.of(404, "Not Found", "No such resource", req.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return ResponseEntity.status(401).body(ApiError.of(401, "Unauthorized", "Invalid email or password", req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(403).body(ApiError.of(403, "Forbidden", "You are not allowed to perform this operation", req.getRequestURI()));
    }

    /** Someone else changed the same record between reading and saving it (optimistic lock on incidents). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleConcurrentChange(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return ResponseEntity.status(409).body(ApiError.of(409, "Conflict",
                "This record was changed by someone else at the same time. Reload it and try again.", req.getRequestURI()));
    }

    /** A database rule (unique value, allowed range) refused the change: the request conflicts with existing data. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleConstraint(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Constraint violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(409).body(ApiError.of(409, "Conflict",
                "The change conflicts with existing data (for example it was already submitted)", req.getRequestURI()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> handleDb(DataAccessException ex, HttpServletRequest req) {
        log.error("Database failure", ex);
        return ResponseEntity.status(503).body(ApiError.of(503, "Service Unavailable", "Database operation failed", req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(500).body(ApiError.of(500, "Internal Server Error", "Unexpected error", req.getRequestURI()));
    }
}

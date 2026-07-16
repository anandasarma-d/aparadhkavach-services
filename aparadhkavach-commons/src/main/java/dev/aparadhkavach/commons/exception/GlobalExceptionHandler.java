package dev.aparadhkavach.commons.exception;

import dev.aparadhkavach.commons.error.ApiError;
import dev.aparadhkavach.commons.error.ApiErrorBody;
import dev.aparadhkavach.commons.error.ErrorCode;
import io.opentelemetry.api.trace.Span;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single {@code @RestControllerAdvice} for every service. One handler method for {@link
 * ApiException} — every subclass already carries its own {@link ErrorCode}/{@link HttpStatus}, so
 * no per-type methods are needed. No {@code Throwable}-level catch-all: genuine JVM {@link Error}s
 * propagate to the container.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiErrorBody> handleApiException(ApiException ex) {
    return toResponse(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), ex.getDetails());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorBody> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    return toResponse(
        HttpStatus.BAD_REQUEST,
        ErrorCode.VAL_INVALID_REQUEST,
        "Request validation failed",
        ex.getBindingResult().getFieldErrors());
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiErrorBody> handleConstraintViolation(ConstraintViolationException ex) {
    return toResponse(
        HttpStatus.BAD_REQUEST,
        ErrorCode.VAL_CONSTRAINT_VIOLATION,
        "Request validation failed",
        ex.getConstraintViolations());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorBody> handleGenericException(Exception ex) {
    return toResponse(
        HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SYS_INTERNAL_ERROR, ex.getMessage(), null);
  }

  private ResponseEntity<ApiErrorBody> toResponse(
      HttpStatus httpStatus, ErrorCode errorCode, String message, Object details) {
    ApiError error =
        new ApiError(
            httpStatus.value(),
            httpStatus.getReasonPhrase().toUpperCase().replace(' ', '_'),
            errorCode.name(),
            message,
            details,
            Span.current().getSpanContext().getTraceId()); // ADR-009 — never a client header
    return ResponseEntity.status(httpStatus).body(new ApiErrorBody(error));
  }
}

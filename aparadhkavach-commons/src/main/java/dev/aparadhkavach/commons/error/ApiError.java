package dev.aparadhkavach.commons.error;

/** The single error response shape for every service, at the correct HTTP status (ADR-020). */
public record ApiError(
    int httpStatus,
    String status,
    String errorCode,
    String message,
    Object details,
    String traceId) {}

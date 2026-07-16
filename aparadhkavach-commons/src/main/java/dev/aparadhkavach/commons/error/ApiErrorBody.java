package dev.aparadhkavach.commons.error;

/** Wraps {@link ApiError} under the single "error" envelope key returned by every service. */
public record ApiErrorBody(ApiError error) {}

package dev.aparadhkavach.commons.exception;

import dev.aparadhkavach.commons.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Abstract base for every domain exception. Carries everything GlobalExceptionHandler needs. */
public abstract class ApiException extends RuntimeException {

  private final ErrorCode errorCode;
  private final HttpStatus httpStatus;
  private final transient Object details;

  protected ApiException(
      ErrorCode errorCode, HttpStatus httpStatus, String message, Object details) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
    this.details = details;
  }

  protected ApiException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
    this(errorCode, httpStatus, message, null);
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public Object getDetails() {
    return details;
  }
}

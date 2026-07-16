package dev.aparadhkavach.commons.exception;

import dev.aparadhkavach.commons.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class ValidationException extends ApiException {
  public ValidationException(String message) {
    super(ErrorCode.VAL_INVALID_REQUEST, HttpStatus.BAD_REQUEST, message);
  }

  public ValidationException(String message, Object details) {
    super(ErrorCode.VAL_INVALID_REQUEST, HttpStatus.BAD_REQUEST, message, details);
  }
}

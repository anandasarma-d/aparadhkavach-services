package dev.aparadhkavach.commons.exception;

import dev.aparadhkavach.commons.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
  public ConflictException(String message) {
    super(ErrorCode.DOM_CONFLICT, HttpStatus.CONFLICT, message);
  }
}

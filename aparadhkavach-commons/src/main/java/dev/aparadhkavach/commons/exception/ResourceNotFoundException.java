package dev.aparadhkavach.commons.exception;

import dev.aparadhkavach.commons.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {
  public ResourceNotFoundException(String message) {
    super(ErrorCode.DOM_RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
  }
}

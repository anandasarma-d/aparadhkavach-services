package dev.aparadhkavach.commons.exception;

import dev.aparadhkavach.commons.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Sarvam STT or Catalyst QuickML unavailability. */
public class ExternalServiceException extends ApiException {
  public ExternalServiceException(String message) {
    super(ErrorCode.SYS_EXTERNAL_SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, message);
  }
}

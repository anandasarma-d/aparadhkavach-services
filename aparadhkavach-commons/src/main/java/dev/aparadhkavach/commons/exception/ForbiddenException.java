package dev.aparadhkavach.commons.exception;

import dev.aparadhkavach.commons.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Named ForbiddenException, not AccessDeniedException, to avoid colliding with
 * org.springframework.security.access.AccessDeniedException's simple name.
 */
public class ForbiddenException extends ApiException {
  public ForbiddenException(String message) {
    super(ErrorCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, message);
  }
}

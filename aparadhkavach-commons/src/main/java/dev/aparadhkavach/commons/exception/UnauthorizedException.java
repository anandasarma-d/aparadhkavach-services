package dev.aparadhkavach.commons.exception;

import dev.aparadhkavach.commons.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 401 Unauthorized — missing/invalid credentials (distinct from 403 Forbidden). */
public class UnauthorizedException extends ApiException {
  public UnauthorizedException(String message) {
    super(ErrorCode.AUTH_UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
  }
}

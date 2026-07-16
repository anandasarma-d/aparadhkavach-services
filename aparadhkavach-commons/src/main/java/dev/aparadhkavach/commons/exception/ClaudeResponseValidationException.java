package dev.aparadhkavach.commons.exception;

import dev.aparadhkavach.commons.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * A Claude response that fails the evidence-backed structure check (Section 6.6 Enforcement 9) —
 * missing one of answer/evidence_sources/related_firs/related_entities/confidence_score/
 * reasoning_summary is a structured error, not a partial success.
 */
public class ClaudeResponseValidationException extends ApiException {
  public ClaudeResponseValidationException(String message) {
    super(ErrorCode.AI_RESPONSE_VALIDATION_FAILED, HttpStatus.BAD_GATEWAY, message);
  }
}

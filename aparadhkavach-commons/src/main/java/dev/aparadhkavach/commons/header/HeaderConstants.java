package dev.aparadhkavach.commons.header;

/**
 * Inbound header names, ADR-021. Values must stay byte-identical (including case) with the mirrored
 * {@code constants.py} in aparadhkavach-stt-service (Section 9.2b) — Python cannot import this
 * class directly.
 */
public final class HeaderConstants {
  private HeaderConstants() {}

  public static final String X_USER_ID = "X-User-Id";
  public static final String X_CORRELATION_ID = "X-Correlation-ID";
}

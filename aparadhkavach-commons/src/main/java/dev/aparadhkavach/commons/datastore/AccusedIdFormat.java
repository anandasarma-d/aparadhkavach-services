package dev.aparadhkavach.commons.datastore;

import dev.aparadhkavach.commons.exception.ValidationException;
import java.util.regex.Pattern;

/**
 * Guards ZCQL path values — Catalyst ZCQL has no prepared-parameter API in the Java SDK we use, so
 * identifiers are validated before interpolation (ADR-021 closed-set discipline for ID shape).
 */
public final class AccusedIdFormat {
  private AccusedIdFormat() {}

  private static final Pattern ACCUSED_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

  public static String requireValid(String accusedId) {
    if (accusedId == null || accusedId.isBlank()) {
      throw new ValidationException("accusedId is required");
    }
    String trimmed = accusedId.trim();
    if (!ACCUSED_ID_PATTERN.matcher(trimmed).matches()) {
      throw new ValidationException("accusedId has invalid format", trimmed);
    }
    return trimmed;
  }
}

package dev.aparadhkavach.commons.datastore;

import dev.aparadhkavach.commons.exception.ValidationException;
import java.util.regex.Pattern;

/**
 * Guards Neo4j entity id path values (Accused / FIR / Victim / …). Same closed-set ID shape as
 * {@link AccusedIdFormat} — Catalyst/Neo4j ids are alphanumeric with {@code _}/{@code -}.
 */
public final class EntityIdFormat {
  private EntityIdFormat() {}

  private static final Pattern ENTITY_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

  public static String requireValid(String entityId) {
    if (entityId == null || entityId.isBlank()) {
      throw new ValidationException("entityId is required");
    }
    String trimmed = entityId.trim();
    if (!ENTITY_ID_PATTERN.matcher(trimmed).matches()) {
      throw new ValidationException("entityId has invalid format", trimmed);
    }
    return trimmed;
  }
}

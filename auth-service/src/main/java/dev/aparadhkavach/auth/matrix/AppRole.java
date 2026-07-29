package dev.aparadhkavach.auth.matrix;

/** Catalyst custom role names — must match console role names exactly. */
public enum AppRole {
  INVESTIGATOR,
  ANALYST,
  SUPERVISOR,
  POLICYMAKER;

  public static AppRole fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("role is required");
    }
    return AppRole.valueOf(raw.trim().toUpperCase());
  }
}

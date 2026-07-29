package dev.aparadhkavach.apigateway.security;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Mirrors auth-service CapabilityMatrix with HTTP path prefixes (mvp2/10). Must stay in sync when
 * views are added (e.g. qa with doc 11).
 */
@Component
public class RolePathAllowlist {

  public enum Role {
    INVESTIGATOR,
    ANALYST,
    SUPERVISOR,
    POLICYMAKER
  }

  private final Map<Role, List<String>> prefixes = new EnumMap<>(Role.class);

  public RolePathAllowlist() {
    prefixes.put(
        Role.INVESTIGATOR,
        List.of("/v1/accusedPersons", "/v1/entities", "/v1/firs", "/v1/auth"));
    prefixes.put(Role.ANALYST, List.of("/v1/analytics", "/v1/accusedPersons", "/v1/auth"));
    prefixes.put(
        Role.SUPERVISOR,
        List.of(
            "/v1/accusedPersons",
            "/v1/analytics",
            "/v1/entities",
            "/v1/firs",
            "/v1/auth"));
    prefixes.put(Role.POLICYMAKER, List.of("/v1/analytics", "/v1/auth"));
  }

  public boolean isAllowed(String roleName, String requestUri) {
    if (roleName == null || requestUri == null) {
      return false;
    }
    Role role;
    try {
      role = Role.valueOf(roleName.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return false;
    }
    String path = requestUri;
    int q = path.indexOf('?');
    if (q >= 0) {
      path = path.substring(0, q);
    }
    for (String prefix : prefixes.get(role)) {
      if (path.equals(prefix) || path.startsWith(prefix + "/") || path.startsWith(prefix + ":")) {
        return true;
      }
    }
    return false;
  }
}

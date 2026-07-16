package dev.aparadhkavach.investigation.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal liveness check (Section 12.3 Stage 6 / 12.6). */
@RestController
public class HealthController {

  private final String serviceName;

  // ADR-021 Part 3: sourced from application.yml's spring.application.name, not hardcoded —
  // the same identifier Section 12.5's SERVICE_NAME env var already externalizes.
  public HealthController(@Value("${spring.application.name}") String serviceName) {
    this.serviceName = serviceName;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP", "service", serviceName);
  }
}

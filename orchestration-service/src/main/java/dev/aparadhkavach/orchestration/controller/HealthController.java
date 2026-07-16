package dev.aparadhkavach.orchestration.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal liveness check (Section 12.3 Stage 6 / 12.6). Does not yet check Neo4j, PgVector, or
 * Claude API reachability — that cascade is Week 1 scope.
 */
@RestController
public class HealthController {

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP", "service", "orchestration-service");
  }
}

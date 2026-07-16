package dev.aparadhkavach.auth.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal liveness check (Section 12.3 Stage 6 / 12.6). */
@RestController
public class HealthController {

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP", "service", "auth-service");
  }
}

package dev.aparadhkavach.orchestration.controller;

import dev.aparadhkavach.orchestration.stt.config.SarvamProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal liveness check (Section 12.3 Stage 6 / 12.6). Does not yet check Neo4j, PgVector, or
 * Claude API reachability — that cascade is Week 1 scope. STT flags are config-only (no outbound
 * probe) so Mic failures can be distinguished from typed Ask without leaking secrets.
 */
@RestController
public class HealthController {

  private final String serviceName;
  private final SarvamProperties sarvamProperties;

  // ADR-021 Part 3: sourced from application.yml's spring.application.name, not hardcoded —
  // the same identifier Section 12.5's SERVICE_NAME env var already externalizes.
  public HealthController(
      @Value("${spring.application.name}") String serviceName, SarvamProperties sarvamProperties) {
    this.serviceName = serviceName;
    this.sarvamProperties = sarvamProperties;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    String sttUrl = sarvamProperties.getSttUrl() == null ? "" : sarvamProperties.getSttUrl().trim();
    String key =
        sarvamProperties.getSttInternalKey() == null
            ? ""
            : sarvamProperties.getSttInternalKey().trim();
    boolean sttUrlConfigured =
        !sttUrl.isBlank()
            && !sttUrl.contains("localhost")
            && !sttUrl.contains("127.0.0.1");
    boolean sttKeyConfigured =
        !key.isBlank() && !key.startsWith("local-dev-placeholder");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "UP");
    body.put("service", serviceName);
    body.put("sttUrlConfigured", sttUrlConfigured);
    body.put("sttKeyConfigured", sttKeyConfigured);
    return body;
  }
}

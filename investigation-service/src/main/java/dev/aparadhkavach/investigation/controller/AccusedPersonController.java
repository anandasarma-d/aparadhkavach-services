package dev.aparadhkavach.investigation.controller;

import dev.aparadhkavach.investigation.dto.AccusedRiskProfileResource;
import dev.aparadhkavach.investigation.service.AccusedRiskProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Custom method on {@code accusedPersons}, matching {@code GET /v1/firs/{firId}:summary} (ADR-020).
 * Returns the composed risk profile resource directly — no envelope.
 */
@RestController
@RequestMapping("/v1/accusedPersons")
public class AccusedPersonController {

  private final AccusedRiskProfileService accusedRiskProfileService;

  public AccusedPersonController(AccusedRiskProfileService accusedRiskProfileService) {
    this.accusedRiskProfileService = accusedRiskProfileService;
  }

  @GetMapping("/{accusedId}:riskProfile")
  public AccusedRiskProfileResource getRiskProfile(@PathVariable String accusedId) {
    return accusedRiskProfileService.getRiskProfile(accusedId);
  }
}

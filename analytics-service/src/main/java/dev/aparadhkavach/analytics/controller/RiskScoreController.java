package dev.aparadhkavach.analytics.controller;

import dev.aparadhkavach.analytics.dto.RiskScoreResource;
import dev.aparadhkavach.analytics.service.RiskScoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single-accused risk score resource for internal composition (Investigation {@code :riskProfile})
 * and any direct Analytics consumers. ADR-020: resource returned directly, no envelope.
 */
@RestController
@RequestMapping("/v1/analytics/riskScores")
public class RiskScoreController {

  private final RiskScoreService riskScoreService;

  public RiskScoreController(RiskScoreService riskScoreService) {
    this.riskScoreService = riskScoreService;
  }

  @GetMapping("/{accusedId}")
  public RiskScoreResource getRiskScore(@PathVariable String accusedId) {
    return riskScoreService.getByAccusedId(accusedId);
  }
}

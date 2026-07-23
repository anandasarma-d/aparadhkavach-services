package dev.aparadhkavach.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** ADR-020 plain resource — camelCase, no envelope. */
public record RiskScoreResource(
    String accusedId,
    BigDecimal riskScore,
    Map<String, Double> topFeatureImportance,
    String scoreId,
    Instant scoredAt,
    String pipelineRunId) {}

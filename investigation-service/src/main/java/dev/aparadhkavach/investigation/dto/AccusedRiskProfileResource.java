package dev.aparadhkavach.investigation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Composed risk profile — ADR-020 plain resource, camelCase, no envelope. */
public record AccusedRiskProfileResource(
    String accusedId,
    String name,
    String addressDistrictId,
    Integer priorOffenseCount,
    BigDecimal riskScore,
    Map<String, Double> topFeatureImportance,
    String scoreId,
    Instant scoredAt,
    String pipelineRunId) {}

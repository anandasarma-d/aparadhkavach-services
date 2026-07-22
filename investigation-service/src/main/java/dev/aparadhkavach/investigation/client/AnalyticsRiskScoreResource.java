package dev.aparadhkavach.investigation.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Analytics Service risk score resource (mirrors Analytics {@code RiskScoreResource}). */
public record AnalyticsRiskScoreResource(
    String accusedId,
    BigDecimal riskScore,
    Map<String, Double> topFeatureImportance,
    String scoreId,
    Instant scoredAt,
    String pipelineRunId) {}

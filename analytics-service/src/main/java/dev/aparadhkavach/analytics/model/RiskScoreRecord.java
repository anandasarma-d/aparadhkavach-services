package dev.aparadhkavach.analytics.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Internal view of one {@code risk_scores} row after DataStore read. */
public record RiskScoreRecord(
    String scoreId,
    String accusedId,
    BigDecimal riskScore,
    Map<String, Double> featureImportance,
    String pipelineRunId,
    Instant scoredAt) {}

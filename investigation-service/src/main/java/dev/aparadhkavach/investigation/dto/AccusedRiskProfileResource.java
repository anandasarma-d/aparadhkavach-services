package dev.aparadhkavach.investigation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Composed risk profile — ADR-020 plain resource, camelCase, no envelope.
 *
 * <p>{@code caseDrivers} (A7 3-Full, Auto/15) carries the real Section 7.5.1 model inputs for this
 * accused and is <em>nullable</em>: it is present only when the optional {@code accused_features}
 * enrichment is available (table imported + row exists). When null, the client falls back to the
 * honest "per-score attribution unavailable" state. {@code topFeatureImportance} remains for the
 * original contract and is still empty for MVP-1 (QuickML predict returned none — D-039).
 */
public record AccusedRiskProfileResource(
    String accusedId,
    String name,
    String addressDistrictId,
    Integer priorOffenseCount,
    BigDecimal riskScore,
    Map<String, Double> topFeatureImportance,
    CaseDriverFeaturesResource caseDrivers,
    String scoreId,
    Instant scoredAt,
    String pipelineRunId) {}

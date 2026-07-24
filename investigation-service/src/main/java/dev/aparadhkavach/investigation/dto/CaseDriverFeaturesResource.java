package dev.aparadhkavach.investigation.dto;

import java.math.BigDecimal;

/**
 * Section 7.5.1 engineered features surfaced as investigator-facing <em>case drivers</em> — the
 * real per-accused model inputs QuickML scored against (ADR-020 plain resource, camelCase, no
 * envelope).
 *
 * <p>These are inputs, not attribution: they explain "what the model looked at for this accused",
 * not "how much each drove this particular score" (QuickML predict never returned per-score
 * importance — D-039). {@code recidivismIntervalAvg} is null for single-offense accused; the client
 * renders that as "not applicable", not zero. See Auto/15 for the full scope-deviation record.
 */
public record CaseDriverFeaturesResource(
    Integer offenseCount,
    BigDecimal recidivismIntervalAvg,
    Integer crimeTypeSeverityMax,
    Integer districtSpread,
    Integer coAccusedCount,
    Integer daysSinceLastOffense) {}

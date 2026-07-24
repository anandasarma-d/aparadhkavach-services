package dev.aparadhkavach.investigation.model;

import java.math.BigDecimal;

/**
 * Internal view of one {@code accused_features} row (Section 7.5.1 engineered features).
 *
 * <p>These are QuickML <em>model inputs</em> derived from FIR/graph records — not per-score
 * attribution. {@code recidivismIntervalAvg} is null for single-offense accused (~85% of rows),
 * which is expected, not a defect. {@code modus_operandi_consistency} (7th feature) is not sourced
 * — Neo4j has no such property (see the Neo4j driver docstring), so it is neither stored nor
 * served.
 */
public record AccusedFeaturesRecord(
    String accusedId,
    Integer offenseCount,
    BigDecimal recidivismIntervalAvg,
    Integer crimeTypeSeverityMax,
    Integer districtSpread,
    Integer coAccusedCount,
    Integer daysSinceLastOffense) {}

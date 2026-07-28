package dev.aparadhkavach.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One hotspot forecast — ADR-020 camelCase, no envelope. */
public record HotspotResource(
    String forecastId,
    String districtId,
    /** Resolved from DataStore {@code districts.district_name} via ROWID; null if unknown. */
    String districtName,
    String crimeType,
    String forecastWindow,
    BigDecimal hotspotScore,
    BigDecimal confidence,
    String pipelineRunId,
    Instant forecastedAt) {}

package dev.aparadhkavach.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One hotspot forecast — ADR-020 camelCase, no envelope. */
public record HotspotResource(
    String forecastId,
    String districtId,
    String crimeType,
    String forecastWindow,
    BigDecimal hotspotScore,
    BigDecimal confidence,
    String pipelineRunId,
    Instant forecastedAt) {}

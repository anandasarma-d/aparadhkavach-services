package dev.aparadhkavach.analytics.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Row from Catalyst {@code hotspot_forecasts}. */
public record HotspotForecastRecord(
    String forecastId,
    String districtId,
    String crimeType,
    String forecastWindow,
    BigDecimal hotspotScore,
    BigDecimal confidence,
    String pipelineRunId,
    Instant forecastedAt) {}

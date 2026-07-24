package dev.aparadhkavach.commons.datastore;

/** Column names on {@link DataStoreTable#HOTSPOT_FORECASTS} (ADR-018 / ADR-021). */
public final class HotspotForecastColumns {
  private HotspotForecastColumns() {}

  public static final String FORECAST_ID = "forecast_id";
  public static final String DISTRICT_ID = "district_id";
  public static final String CRIME_TYPE = "crime_type";
  public static final String FORECAST_WINDOW = "forecast_window";
  public static final String HOTSPOT_SCORE = "hotspot_score";
  public static final String CONFIDENCE = "confidence";
  public static final String PIPELINE_RUN_ID = "pipeline_run_id";
  public static final String FORECASTED_AT = "forecasted_at";
}

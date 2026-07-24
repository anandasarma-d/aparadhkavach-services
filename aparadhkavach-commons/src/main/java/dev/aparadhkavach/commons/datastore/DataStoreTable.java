package dev.aparadhkavach.commons.datastore;

/**
 * Catalyst DataStore table names (ADR-018 snake_case plural; ADR-021 — no inline magic strings).
 */
public enum DataStoreTable {
  ACCUSED_PERSONS("accused_persons"),
  ACCUSED_FEATURES("accused_features"),
  RISK_SCORES("risk_scores"),
  HOTSPOT_FORECASTS("hotspot_forecasts"),
  FIRS("firs"),
  DISTRICTS("districts");

  private final String tableName;

  DataStoreTable(String tableName) {
    this.tableName = tableName;
  }

  public String tableName() {
    return tableName;
  }
}

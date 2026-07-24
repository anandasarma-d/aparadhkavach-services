package dev.aparadhkavach.commons.datastore;

/**
 * Column names on {@link DataStoreTable#ACCUSED_FEATURES} (ADR-018 / ADR-021).
 *
 * <p>These are the Section 7.5.1 engineered ACCUSED_FEATURES — the same values fed to the QuickML
 * risk-scorer as model inputs (not per-score attribution). Derived from Neo4j/FIR traversal by
 * {@code aparadhkavach-data-generator/scripts/neo4j_accused_features_driver.py} and imported into
 * DataStore for read-time serving (deviation D-039 / A7 3-Full — see Auto/15). The training-only
 * {@code risk_label} target column is deliberately NOT served and must not be imported here.
 */
public final class AccusedFeatureColumns {
  private AccusedFeatureColumns() {}

  public static final String ACCUSED_ID = "accused_id";
  public static final String OFFENSE_COUNT = "offense_count";
  public static final String RECIDIVISM_INTERVAL_AVG = "recidivism_interval_avg";
  public static final String CRIME_TYPE_SEVERITY_MAX = "crime_type_severity_max";
  public static final String DISTRICT_SPREAD = "district_spread";
  public static final String CO_ACCUSED_COUNT = "co_accused_count";
  public static final String DAYS_SINCE_LAST_OFFENSE = "days_since_last_offense";
}

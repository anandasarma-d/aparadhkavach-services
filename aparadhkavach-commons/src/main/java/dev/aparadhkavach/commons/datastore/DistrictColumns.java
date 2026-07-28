package dev.aparadhkavach.commons.datastore;

/** Column names on {@link DataStoreTable#DISTRICTS} (ADR-018 / ADR-021). */
public final class DistrictColumns {
  private DistrictColumns() {}

  /** Catalyst system primary key — hotspot_forecasts.district_id stores this as a string. */
  public static final String ROWID = "ROWID";

  public static final String DISTRICT_ID = "district_id";
  public static final String DISTRICT_NAME = "district_name";
  public static final String REGION = "region";
}

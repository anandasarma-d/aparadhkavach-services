package dev.aparadhkavach.commons.datastore;

/** Column names on {@link DataStoreTable#ACCUSED_PERSONS} (ADR-018 / ADR-021). */
public final class AccusedPersonColumns {
  private AccusedPersonColumns() {}

  public static final String ACCUSED_ID = "accused_id";
  public static final String NAME = "name";
  public static final String ADDRESS_DISTRICT_ID = "address_district_id";
  public static final String PRIOR_OFFENSE_COUNT = "prior_offense_count";
}

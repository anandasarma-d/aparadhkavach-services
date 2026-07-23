package dev.aparadhkavach.commons.risk;

/**
 * Section 7.5.1 feature declaration order — used as the tie-break when selecting top-N feature
 * importance values (ADR-021).
 */
public enum RiskFeature {
  OFFENSE_COUNT("offense_count"),
  RECIDIVISM_INTERVAL_AVG("recidivism_interval_avg"),
  CRIME_TYPE_SEVERITY_MAX("crime_type_severity_max"),
  DISTRICT_SPREAD("district_spread"),
  CO_ACCUSED_COUNT("co_accused_count"),
  DAYS_SINCE_LAST_OFFENSE("days_since_last_offense"),
  MODUS_OPERANDI_CONSISTENCY("modus_operandi_consistency");

  private final String featureKey;

  RiskFeature(String featureKey) {
    this.featureKey = featureKey;
  }

  public String featureKey() {
    return featureKey;
  }

  public static int declarationOrder(String featureKey) {
    for (RiskFeature feature : values()) {
      if (feature.featureKey.equals(featureKey)) {
        return feature.ordinal();
      }
    }
    return Integer.MAX_VALUE;
  }
}

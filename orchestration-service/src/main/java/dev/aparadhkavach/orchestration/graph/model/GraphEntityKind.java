package dev.aparadhkavach.orchestration.graph.model;

/**
 * Maps business entity ids (ACC-…, FIR-…) to a Neo4j label + id property so lookups use Section 5.4
 * indexes instead of multi-branch UNION scans (D-059).
 */
public enum GraphEntityKind {
  ACCUSED("Accused", "accused_id"),
  FIR("FIR", "fir_id"),
  VICTIM("Victim", "victim_id"),
  WITNESS("Witness", "witness_id"),
  LOCATION("Location", "location_id"),
  VEHICLE("Vehicle", "vehicle_id"),
  PHONE("PhoneNumber", "phone_id"),
  OFFICER("InvestigationOfficer", "officer_id"),
  CRIME_TYPE("CrimeType", "type_id");

  private final String label;
  private final String idProperty;

  GraphEntityKind(String label, String idProperty) {
    this.label = label;
    this.idProperty = idProperty;
  }

  public String label() {
    return label;
  }

  public String idProperty() {
    return idProperty;
  }

  /** Best-effort kind from id prefix; null → caller uses slow multi-label fallback. */
  public static GraphEntityKind fromEntityId(String entityId) {
    if (entityId == null || entityId.isBlank()) {
      return null;
    }
    String id = entityId.trim();
    if (id.regionMatches(true, 0, "ACC-", 0, 4)) {
      return ACCUSED;
    }
    if (id.regionMatches(true, 0, "FIR-", 0, 4)) {
      return FIR;
    }
    if (id.regionMatches(true, 0, "VIC-", 0, 4) || id.regionMatches(true, 0, "VICTIM-", 0, 7)) {
      return VICTIM;
    }
    if (id.regionMatches(true, 0, "WIT-", 0, 4)) {
      return WITNESS;
    }
    if (id.regionMatches(true, 0, "LOC-", 0, 4)) {
      return LOCATION;
    }
    if (id.regionMatches(true, 0, "VEH-", 0, 4)) {
      return VEHICLE;
    }
    if (id.regionMatches(true, 0, "PHN-", 0, 4) || id.regionMatches(true, 0, "PHONE-", 0, 6)) {
      return PHONE;
    }
    if (id.regionMatches(true, 0, "OFF-", 0, 4)) {
      return OFFICER;
    }
    if (id.regionMatches(true, 0, "CT-", 0, 3) || id.regionMatches(true, 0, "TYPE-", 0, 5)) {
      return CRIME_TYPE;
    }
    return null;
  }
}

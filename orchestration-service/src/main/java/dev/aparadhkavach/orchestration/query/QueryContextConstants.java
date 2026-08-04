package dev.aparadhkavach.orchestration.query;

/**
 * Labels for the Claude CONTEXT block assembled by {@code QueryContextAssembler} (mvp2/11). Keeps
 * field names out of service logic so wording can be reviewed in one place.
 */
public final class QueryContextConstants {

  private QueryContextConstants() {}

  public static final String SEED_KIND = "seedKind=";
  public static final String SEED_ID = "seedId=";

  public static final String RISK_PROFILE_HEADER = "investigationRiskProfile:\n";
  public static final String RISK_PROFILE_UNAVAILABLE = "investigationRiskProfile: (unavailable)\n";
  public static final String RISK_ACCUSED_ID = "  accusedId=";
  public static final String RISK_NAME = "  name=";
  public static final String RISK_ADDRESS_DISTRICT_ID = "  addressDistrictId=";
  public static final String RISK_PRIOR_OFFENSE_COUNT = "  priorOffenseCount=";
  public static final String RISK_SCORE = "  riskScore=";

  public static final String GRAPH_DEPTH = "graphNeighborhoodDepth=";
  public static final String GRAPH_TRUNCATED = "graphNeighborhoodTruncated=";
  public static final String NODES_HEADER = "nodes:\n";
  public static final String EDGES_HEADER = "edges:\n";

  public static final String NODE_PREFIX = "  - id=";
  public static final String NODE_TYPE = " type=";
  public static final String NODE_LABEL = " label=";

  public static final String EDGE_PREFIX = "  - ";
  public static final String EDGE_REL_OPEN = " -[";
  public static final String EDGE_REL_CLOSE = "]-> ";

  /** Missing string fields in CONTEXT. */
  public static final String MISSING_VALUE = "—";

  /** Id conventions used when listing related FIRs. */
  public static final String FIR_ID_PREFIX = "FIR-";
  public static final String FIR_NODE_TYPE = "FIR";
}

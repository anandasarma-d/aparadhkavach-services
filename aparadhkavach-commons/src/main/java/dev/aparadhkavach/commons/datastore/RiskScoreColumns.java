package dev.aparadhkavach.commons.datastore;

/** Column names on {@link DataStoreTable#RISK_SCORES} (ADR-018 / ADR-021). */
public final class RiskScoreColumns {
  private RiskScoreColumns() {}

  public static final String SCORE_ID = "score_id";
  public static final String ACCUSED_ID = "accused_id";
  public static final String RISK_SCORE = "risk_score";
  public static final String FEATURE_IMPORTANCE = "feature_importance";
  public static final String PIPELINE_RUN_ID = "pipeline_run_id";
  public static final String SCORED_AT = "scored_at";
}

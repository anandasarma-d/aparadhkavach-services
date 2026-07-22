package dev.aparadhkavach.analytics.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aparadhkavach.analytics.model.RiskScoreRecord;
import dev.aparadhkavach.commons.datastore.AccusedIdFormat;
import dev.aparadhkavach.commons.datastore.DataStoreTable;
import dev.aparadhkavach.commons.datastore.RiskScoreColumns;
import dev.aparadhkavach.commons.datastore.ZcqlExecutor;
import dev.aparadhkavach.commons.datastore.ZcqlRowMapper;
import dev.aparadhkavach.commons.exception.ExternalServiceException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RiskScoreRepository {

  private static final TypeReference<Map<String, Double>> FEATURE_MAP_TYPE =
      new TypeReference<>() {};

  private final ZcqlExecutor zcqlExecutor;
  private final ObjectMapper objectMapper;

  public RiskScoreRepository(ZcqlExecutor zcqlExecutor, ObjectMapper objectMapper) {
    this.zcqlExecutor = zcqlExecutor;
    this.objectMapper = objectMapper;
  }

  public Optional<RiskScoreRecord> findLatestByAccusedId(String accusedId) {
    String id = AccusedIdFormat.requireValid(accusedId);
    String table = DataStoreTable.RISK_SCORES.tableName();
    String zcql =
        "SELECT * FROM "
            + table
            + " WHERE "
            + RiskScoreColumns.ACCUSED_ID
            + " = '"
            + id
            + "' ORDER BY "
            + RiskScoreColumns.SCORED_AT
            + " DESC LIMIT 1";
    try {
      List<Map<String, Object>> rows = zcqlExecutor.execute(zcql);
      if (rows.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(toRecord(rows.getFirst()));
    } catch (ExternalServiceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ExternalServiceException("Catalyst DataStore risk_scores read failed: " + ex.getMessage());
    }
  }

  private RiskScoreRecord toRecord(Map<String, Object> row) {
    return new RiskScoreRecord(
        ZcqlRowMapper.stringColumn(row, RiskScoreColumns.SCORE_ID),
        ZcqlRowMapper.stringColumn(row, RiskScoreColumns.ACCUSED_ID),
        toBigDecimal(ZcqlRowMapper.column(row, RiskScoreColumns.RISK_SCORE)),
        parseFeatureImportance(
            ZcqlRowMapper.stringColumn(row, RiskScoreColumns.FEATURE_IMPORTANCE)),
        ZcqlRowMapper.stringColumn(row, RiskScoreColumns.PIPELINE_RUN_ID),
        parseInstant(ZcqlRowMapper.stringColumn(row, RiskScoreColumns.SCORED_AT)));
  }

  private Map<String, Double> parseFeatureImportance(String json) {
    if (json == null || json.isBlank()) {
      return Collections.emptyMap();
    }
    try {
      return objectMapper.readValue(json, FEATURE_MAP_TYPE);
    } catch (Exception ex) {
      throw new ExternalServiceException(
          "Invalid feature_importance JSON on risk_scores row: " + ex.getMessage());
    }
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bigDecimal) {
      return bigDecimal;
    }
    if (value instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }
    return new BigDecimal(String.valueOf(value));
  }

  private static Instant parseInstant(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException ignored) {
      // Catalyst often stores DATETIME without zone — treat as UTC.
      LocalDateTime local =
          LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      return local.toInstant(ZoneOffset.UTC);
    }
  }
}

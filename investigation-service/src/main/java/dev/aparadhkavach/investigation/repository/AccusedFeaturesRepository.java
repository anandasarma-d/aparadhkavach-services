package dev.aparadhkavach.investigation.repository;

import dev.aparadhkavach.commons.datastore.AccusedFeatureColumns;
import dev.aparadhkavach.commons.datastore.AccusedIdFormat;
import dev.aparadhkavach.commons.datastore.DataStoreTable;
import dev.aparadhkavach.commons.datastore.ZcqlExecutor;
import dev.aparadhkavach.commons.datastore.ZcqlRowMapper;
import dev.aparadhkavach.investigation.model.AccusedFeaturesRecord;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Reads the Section 7.5.1 engineered features from {@link DataStoreTable#ACCUSED_FEATURES} to
 * enrich the composed risk profile with real per-accused model inputs (A7 3-Full — see Auto/15).
 *
 * <p><strong>Deliberately degrades gracefully.</strong> This is optional enrichment layered onto an
 * existing, shipped contract: if the {@code accused_features} table has not been created / imported
 * yet (a one-time console + {@code ds:import} step, D-039), or a given accused has no feature row,
 * this returns {@link Optional#empty()} rather than throwing — so the profile endpoint keeps
 * working and the UI simply falls back to its honest "attribution unavailable" state. Contrast with
 * {@link AccusedPersonsRepository} / {@code RiskScoreRepository}, which throw on failure because
 * their data is mandatory for the response.
 */
@Repository
public class AccusedFeaturesRepository {

  private static final Logger log = LoggerFactory.getLogger(AccusedFeaturesRepository.class);

  private final ZcqlExecutor zcqlExecutor;

  public AccusedFeaturesRepository(ZcqlExecutor zcqlExecutor) {
    this.zcqlExecutor = zcqlExecutor;
  }

  public Optional<AccusedFeaturesRecord> findByAccusedId(String accusedId) {
    String id = AccusedIdFormat.requireValid(accusedId);
    String table = DataStoreTable.ACCUSED_FEATURES.tableName();
    String zcql =
        "SELECT * FROM "
            + table
            + " WHERE "
            + AccusedFeatureColumns.ACCUSED_ID
            + " = '"
            + id
            + "' LIMIT 1";
    try {
      List<Map<String, Object>> rows = zcqlExecutor.execute(zcql);
      if (rows.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(toRecord(rows.getFirst()));
    } catch (Exception ex) {
      // Optional enrichment — never fail the profile if the table is absent or unreadable.
      log.warn(
          "accused_features read skipped for accusedId={} (optional enrichment): {}",
          id,
          ex.getMessage());
      return Optional.empty();
    }
  }

  private static AccusedFeaturesRecord toRecord(Map<String, Object> row) {
    return new AccusedFeaturesRecord(
        ZcqlRowMapper.stringColumn(row, AccusedFeatureColumns.ACCUSED_ID),
        toInteger(ZcqlRowMapper.column(row, AccusedFeatureColumns.OFFENSE_COUNT)),
        toBigDecimal(ZcqlRowMapper.column(row, AccusedFeatureColumns.RECIDIVISM_INTERVAL_AVG)),
        toInteger(ZcqlRowMapper.column(row, AccusedFeatureColumns.CRIME_TYPE_SEVERITY_MAX)),
        toInteger(ZcqlRowMapper.column(row, AccusedFeatureColumns.DISTRICT_SPREAD)),
        toInteger(ZcqlRowMapper.column(row, AccusedFeatureColumns.CO_ACCUSED_COUNT)),
        toInteger(ZcqlRowMapper.column(row, AccusedFeatureColumns.DAYS_SINCE_LAST_OFFENSE)));
  }

  private static Integer toInteger(Object value) {
    if (value == null || (value instanceof String s && s.isBlank())) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    return Integer.valueOf(String.valueOf(value).trim());
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value == null || (value instanceof String s && s.isBlank())) {
      return null;
    }
    if (value instanceof BigDecimal bigDecimal) {
      return bigDecimal;
    }
    if (value instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }
    return new BigDecimal(String.valueOf(value).trim());
  }
}

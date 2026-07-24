package dev.aparadhkavach.analytics.repository;

import dev.aparadhkavach.analytics.model.HotspotForecastRecord;
import dev.aparadhkavach.commons.datastore.DataStoreTable;
import dev.aparadhkavach.commons.datastore.HotspotForecastColumns;
import dev.aparadhkavach.commons.datastore.ZcqlExecutor;
import dev.aparadhkavach.commons.datastore.ZcqlRowMapper;
import dev.aparadhkavach.commons.exception.ExternalServiceException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class HotspotForecastRepository {

  private final ZcqlExecutor zcqlExecutor;

  public HotspotForecastRepository(ZcqlExecutor zcqlExecutor) {
    this.zcqlExecutor = zcqlExecutor;
  }

  /**
   * Returns hotspot rows ordered by score descending. Catalyst ZCQL paging is limited — callers
   * apply offset/limit in memory for MVP-1.
   */
  public List<HotspotForecastRecord> findAllOrderedByScoreDesc() {
    String table = DataStoreTable.HOTSPOT_FORECASTS.tableName();
    String zcql =
        "SELECT * FROM "
            + table
            + " ORDER BY "
            + HotspotForecastColumns.HOTSPOT_SCORE
            + " DESC";
    try {
      List<Map<String, Object>> rows = zcqlExecutor.execute(zcql);
      List<HotspotForecastRecord> records = new ArrayList<>(rows.size());
      for (Map<String, Object> row : rows) {
        records.add(toRecord(row));
      }
      return records;
    } catch (ExternalServiceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ExternalServiceException(
          "Catalyst DataStore hotspot_forecasts read failed: " + ex.getMessage());
    }
  }

  private HotspotForecastRecord toRecord(Map<String, Object> row) {
    return new HotspotForecastRecord(
        ZcqlRowMapper.stringColumn(row, HotspotForecastColumns.FORECAST_ID),
        ZcqlRowMapper.stringColumn(row, HotspotForecastColumns.DISTRICT_ID),
        ZcqlRowMapper.stringColumn(row, HotspotForecastColumns.CRIME_TYPE),
        ZcqlRowMapper.stringColumn(row, HotspotForecastColumns.FORECAST_WINDOW),
        toBigDecimal(ZcqlRowMapper.column(row, HotspotForecastColumns.HOTSPOT_SCORE)),
        toBigDecimal(ZcqlRowMapper.column(row, HotspotForecastColumns.CONFIDENCE)),
        ZcqlRowMapper.stringColumn(row, HotspotForecastColumns.PIPELINE_RUN_ID),
        parseInstant(ZcqlRowMapper.stringColumn(row, HotspotForecastColumns.FORECASTED_AT)));
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
    String raw = String.valueOf(value).trim();
    if (raw.isEmpty()) {
      return null;
    }
    return new BigDecimal(raw);
  }

  private static Instant parseInstant(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException ignored) {
      LocalDateTime local =
          LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      return local.toInstant(ZoneOffset.UTC);
    }
  }
}

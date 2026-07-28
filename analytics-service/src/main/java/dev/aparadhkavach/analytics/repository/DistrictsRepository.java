package dev.aparadhkavach.analytics.repository;

import dev.aparadhkavach.commons.datastore.DataStoreTable;
import dev.aparadhkavach.commons.datastore.DistrictColumns;
import dev.aparadhkavach.commons.datastore.ZcqlExecutor;
import dev.aparadhkavach.commons.datastore.ZcqlRowMapper;
import dev.aparadhkavach.commons.exception.ExternalServiceException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** Reference data from Catalyst {@code districts} — ROWID → display name. */
@Repository
public class DistrictsRepository {

  private final ZcqlExecutor zcqlExecutor;

  public DistrictsRepository(ZcqlExecutor zcqlExecutor) {
    this.zcqlExecutor = zcqlExecutor;
  }

  /**
   * Map of Catalyst {@code districts.ROWID} (string) → {@code district_name}. Empty if the table is
   * empty; never invents names.
   */
  public Map<String, String> findRowIdToNameMap() {
    String table = DataStoreTable.DISTRICTS.tableName();
    String zcql =
        "SELECT "
            + DistrictColumns.ROWID
            + ", "
            + DistrictColumns.DISTRICT_NAME
            + " FROM "
            + table;
    try {
      List<Map<String, Object>> rows = zcqlExecutor.execute(zcql);
      Map<String, String> map = new HashMap<>(rows.size());
      for (Map<String, Object> row : rows) {
        String rowId = ZcqlRowMapper.stringColumn(row, DistrictColumns.ROWID);
        String name = ZcqlRowMapper.stringColumn(row, DistrictColumns.DISTRICT_NAME);
        if (rowId != null && !rowId.isBlank() && name != null && !name.isBlank()) {
          map.put(rowId, name);
        }
      }
      return map;
    } catch (ExternalServiceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ExternalServiceException(
          "Catalyst DataStore districts read failed: " + ex.getMessage());
    }
  }
}

package dev.aparadhkavach.commons.catalyst;

import com.zc.common.ZCProject;
import com.zc.component.object.ZCRowObject;
import com.zc.component.zcql.ZCQL;
import dev.aparadhkavach.commons.datastore.DataStoreTable;
import dev.aparadhkavach.commons.datastore.ZcqlExecutor;
import dev.aparadhkavach.commons.datastore.ZcqlRowMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Catalyst SDK-backed {@link ZcqlExecutor} (Section 6.1 DataStoreClient pattern). */
@Component
public class CatalystZcqlExecutor implements ZcqlExecutor {

  private final CatalystSdkInitializer initializer;

  public CatalystZcqlExecutor(CatalystSdkInitializer initializer) {
    this.initializer = initializer;
  }

  @Override
  public List<Map<String, Object>> execute(String zcql) throws Exception {
    ZCProject project = initializer.projectOrNull();
    ZCQL zcqlClient = project == null ? ZCQL.getInstance() : ZCQL.getInstance(project);
    ArrayList<ZCRowObject> rows = zcqlClient.executeQuery(zcql);
    DataStoreTable table = inferTable(zcql);
    List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
    for (ZCRowObject row : rows) {
      mapped.add(ZcqlRowMapper.toFlatMap(row, table));
    }
    return mapped;
  }

  private static DataStoreTable inferTable(String zcql) {
    String lower = zcql.toLowerCase();
    for (DataStoreTable table : DataStoreTable.values()) {
      if (lower.contains(" from " + table.tableName())
          || lower.contains(" from " + table.tableName() + " ")) {
        return table;
      }
    }
    // Fallback: still flatten with accused_persons rules (bare keys preserved).
    return DataStoreTable.ACCUSED_PERSONS;
  }
}

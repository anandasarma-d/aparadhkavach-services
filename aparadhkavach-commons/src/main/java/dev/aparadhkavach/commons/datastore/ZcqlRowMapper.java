package dev.aparadhkavach.commons.datastore;

import com.zc.component.object.ZCRowObject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.simple.JSONObject;

/** Normalizes Catalyst ZCQL {@link ZCRowObject} shapes into flat column maps. */
public final class ZcqlRowMapper {
  private ZcqlRowMapper() {}

  @SuppressWarnings("unchecked")
  public static Map<String, Object> toFlatMap(ZCRowObject row, DataStoreTable table) {
    Map<String, Object> flat = new LinkedHashMap<>();
    JSONObject raw = row.getRowObject();
    if (raw == null) {
      return flat;
    }

    Object tableBucket = raw.get(table.tableName());
    if (tableBucket instanceof JSONObject nested) {
      nested.forEach((key, value) -> flat.put(String.valueOf(key), value));
      return flat;
    }

    raw.forEach(
        (key, value) -> {
          String name = String.valueOf(key);
          String prefix = table.tableName() + ".";
          if (name.startsWith(prefix)) {
            flat.put(name.substring(prefix.length()), value);
          } else {
            flat.put(name, value);
          }
        });
    return flat;
  }

  public static Object column(Map<String, Object> row, String column) {
    return row.get(column);
  }

  public static String stringColumn(Map<String, Object> row, String column) {
    Object value = column(row, column);
    return value == null ? null : String.valueOf(value);
  }
}

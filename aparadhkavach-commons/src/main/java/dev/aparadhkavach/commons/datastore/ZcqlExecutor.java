package dev.aparadhkavach.commons.datastore;

import java.util.List;
import java.util.Map;

/** Thin port over Catalyst ZCQL — mockable in unit tests without the SDK. */
public interface ZcqlExecutor {

  /**
   * Executes a ZCQL statement and returns rows as flat column→value maps (table-qualified keys are
   * normalized to bare column names when present).
   */
  List<Map<String, Object>> execute(String zcql) throws Exception;
}

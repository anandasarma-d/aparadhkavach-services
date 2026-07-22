package dev.aparadhkavach.investigation.repository;

import dev.aparadhkavach.commons.datastore.AccusedIdFormat;
import dev.aparadhkavach.commons.datastore.AccusedPersonColumns;
import dev.aparadhkavach.commons.datastore.DataStoreTable;
import dev.aparadhkavach.commons.datastore.ZcqlExecutor;
import dev.aparadhkavach.commons.datastore.ZcqlRowMapper;
import dev.aparadhkavach.commons.exception.ExternalServiceException;
import dev.aparadhkavach.investigation.model.AccusedPersonRecord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AccusedPersonsRepository {

  private final ZcqlExecutor zcqlExecutor;

  public AccusedPersonsRepository(ZcqlExecutor zcqlExecutor) {
    this.zcqlExecutor = zcqlExecutor;
  }

  public Optional<AccusedPersonRecord> findByAccusedId(String accusedId) {
    String id = AccusedIdFormat.requireValid(accusedId);
    String table = DataStoreTable.ACCUSED_PERSONS.tableName();
    String zcql =
        "SELECT * FROM "
            + table
            + " WHERE "
            + AccusedPersonColumns.ACCUSED_ID
            + " = '"
            + id
            + "' LIMIT 1";
    try {
      List<Map<String, Object>> rows = zcqlExecutor.execute(zcql);
      if (rows.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(toRecord(rows.getFirst()));
    } catch (ExternalServiceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ExternalServiceException(
          "Catalyst DataStore accused_persons read failed: " + ex.getMessage());
    }
  }

  private static AccusedPersonRecord toRecord(Map<String, Object> row) {
    return new AccusedPersonRecord(
        ZcqlRowMapper.stringColumn(row, AccusedPersonColumns.ACCUSED_ID),
        ZcqlRowMapper.stringColumn(row, AccusedPersonColumns.NAME),
        ZcqlRowMapper.stringColumn(row, AccusedPersonColumns.ADDRESS_DISTRICT_ID),
        toInteger(ZcqlRowMapper.column(row, AccusedPersonColumns.PRIOR_OFFENSE_COUNT)));
  }

  private static Integer toInteger(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    return Integer.valueOf(String.valueOf(value));
  }
}

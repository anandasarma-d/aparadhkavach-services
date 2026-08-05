package dev.aparadhkavach.orchestration.search.repository;

import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import dev.aparadhkavach.orchestration.search.model.SimilarCase;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Raw JDBC ANN against denormalized Supabase {@code fir_embeddings}. Spring AI {@code
 * PgVectorStore} stays excluded (needs Voyage {@code EmbeddingModel}); probe = stored row vector
 * (Auto/18 A11).
 */
@Repository
public class FirEmbeddingsRepository {

  private static final String SIMILAR_SQL =
      """
      WITH probe AS (
        SELECT embedding FROM fir_embeddings WHERE fir_id = :firId
      )
      SELECT f.fir_id,
             f.district,
             f.crime_type,
             f.date_filed,
             f.status,
             (1.0 - (f.embedding <=> (SELECT embedding FROM probe))) AS similarity_score
      FROM fir_embeddings f
      WHERE EXISTS (SELECT 1 FROM probe)
        AND f.fir_id <> :firId
      ORDER BY f.embedding <=> (SELECT embedding FROM probe)
      LIMIT :limit
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public FirEmbeddingsRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Cosine nearest neighbors for {@code firId}, excluding self. Throws {@link
   * ResourceNotFoundException} when the probe FIR is absent from {@code fir_embeddings}.
   *
   * <p>Single round-trip (D-064): probe absence is detected via a second cheap EXISTS only when the
   * ANN result is empty — avoids two cold JDBC calls on every happy path.
   */
  public List<SimilarCase> findSimilar(String firId, int limit) {
    List<SimilarCase> cases =
        jdbc.query(
            SIMILAR_SQL,
            Map.of("firId", firId, "limit", limit),
            (rs, rowNum) ->
                new SimilarCase(
                    rs.getString("fir_id"),
                    rs.getDouble("similarity_score"),
                    rs.getString("district"),
                    rs.getString("crime_type"),
                    toLocalDate(rs.getDate("date_filed")),
                    rs.getString("status")));
    if (!cases.isEmpty()) {
      return cases;
    }
    Boolean exists =
        jdbc.getJdbcTemplate()
            .queryForObject(
                "SELECT EXISTS(SELECT 1 FROM fir_embeddings WHERE fir_id = ?)",
                Boolean.class,
                firId);
    if (!Boolean.TRUE.equals(exists)) {
      throw new ResourceNotFoundException("No embedding for firId=" + firId);
    }
    return List.of();
  }

  private static LocalDate toLocalDate(Date date) {
    return date == null ? null : date.toLocalDate();
  }
}

package dev.aparadhkavach.orchestration.search.service;

import dev.aparadhkavach.commons.datastore.EntityIdFormat;
import dev.aparadhkavach.commons.exception.ExternalServiceException;
import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.search.config.VectorProperties;
import dev.aparadhkavach.orchestration.search.model.FirTextSearchResult;
import dev.aparadhkavach.orchestration.search.model.SimilarCase;
import dev.aparadhkavach.orchestration.search.model.SimilarCasesResult;
import dev.aparadhkavach.orchestration.search.repository.FirEmbeddingsRepository;
import dev.aparadhkavach.orchestration.search.voyage.VoyageEmbeddingClient;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Similar-case discovery (A11 + typed-text). FIR-id path uses stored vectors only (no Voyage).
 * Free-text path embeds with Voyage {@code input_type=query} then ANN.
 */
@Service
public class SimilarCasesService {

  private final FirEmbeddingsRepository repository;
  private final VectorProperties vectorProperties;
  private final VoyageEmbeddingClient voyageEmbeddingClient;

  public SimilarCasesService(
      FirEmbeddingsRepository repository,
      VectorProperties vectorProperties,
      VoyageEmbeddingClient voyageEmbeddingClient) {
    this.repository = repository;
    this.vectorProperties = vectorProperties;
    this.voyageEmbeddingClient = voyageEmbeddingClient;
  }

  public SimilarCasesResult findSimilar(String rawFirId, Integer requestedLimit) {
    String firId = EntityIdFormat.requireValid(rawFirId);
    int limit = clampLimit(requestedLimit);
    try {
      List<SimilarCase> cases = repository.findSimilar(firId, limit);
      return new SimilarCasesResult(firId, limit, List.copyOf(cases));
    } catch (DataAccessException ex) {
      throw new ExternalServiceException(
          "Similar-cases store temporarily unavailable; retry shortly");
    }
  }

  /**
   * Semantic neighbors for officer-typed narrative text (Auto/18 free-text). Not a crime-type SQL
   * filter — ranks FIRs whose narratives match the phrase. Neighbors below {@link
   * VectorProperties#getTextMinSimilarity()} are dropped (may return empty).
   */
  public FirTextSearchResult searchByText(String rawQuery, Integer requestedLimit) {
    String query = rawQuery == null ? "" : rawQuery.trim();
    if (query.isBlank()) {
      throw new ValidationException("Query text q is required");
    }
    // Single crime-type words (e.g. "theft") are slow/weak vs Voyage query+ANN and often trip
    // AppSail response budget — require a short modus phrase (Auto/18 honesty).
    if (query.split("\\s+").length < 2 || query.length() < 12) {
      throw new ValidationException(
          "Use a short narrative phrase (e.g. vehicle theft from parking lot), not a single word");
    }
    int limit = clampLimit(requestedLimit);
    float[] embedding = voyageEmbeddingClient.embedQuery(query);
    try {
      List<SimilarCase> cases = repository.findSimilarByEmbedding(embedding, limit);
      return new FirTextSearchResult(query, limit, applyTextMinSimilarity(cases));
    } catch (DataAccessException ex) {
      throw new ExternalServiceException(
          "Similar-cases store temporarily unavailable; retry shortly");
    }
  }

  /** Drop weak ANN neighbors so officers are not shown unrelated crime types as “matches”. */
  List<SimilarCase> applyTextMinSimilarity(List<SimilarCase> cases) {
    double min = vectorProperties.getTextMinSimilarity();
    if (cases == null || cases.isEmpty() || min <= 0) {
      return cases == null ? List.of() : List.copyOf(cases);
    }
    return cases.stream().filter(c -> c.similarityScore() >= min).toList();
  }

  int clampLimit(Integer requestedLimit) {
    int max = Math.max(1, vectorProperties.getMaxTopK());
    int fallback = Math.min(Math.max(1, vectorProperties.getTopK()), max);
    if (requestedLimit == null) {
      return fallback;
    }
    if (requestedLimit < 1) {
      return 1;
    }
    return Math.min(requestedLimit, max);
  }
}

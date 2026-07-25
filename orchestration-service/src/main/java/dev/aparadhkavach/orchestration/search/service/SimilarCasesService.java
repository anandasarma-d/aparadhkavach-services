package dev.aparadhkavach.orchestration.search.service;

import dev.aparadhkavach.commons.datastore.EntityIdFormat;
import dev.aparadhkavach.orchestration.search.config.VectorProperties;
import dev.aparadhkavach.orchestration.search.model.SimilarCase;
import dev.aparadhkavach.orchestration.search.model.SimilarCasesResult;
import dev.aparadhkavach.orchestration.search.repository.FirEmbeddingsRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Similar-case discovery (A11). Clamps {@code limit} to {@code [1, maxTopK]}; never fabricates
 * neighbors; never calls Voyage on the read path.
 */
@Service
public class SimilarCasesService {

  private final FirEmbeddingsRepository repository;
  private final VectorProperties vectorProperties;

  public SimilarCasesService(
      FirEmbeddingsRepository repository, VectorProperties vectorProperties) {
    this.repository = repository;
    this.vectorProperties = vectorProperties;
  }

  public SimilarCasesResult findSimilar(String rawFirId, Integer requestedLimit) {
    String firId = EntityIdFormat.requireValid(rawFirId);
    int limit = clampLimit(requestedLimit);
    List<SimilarCase> cases = repository.findSimilar(firId, limit);
    return new SimilarCasesResult(firId, limit, List.copyOf(cases));
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

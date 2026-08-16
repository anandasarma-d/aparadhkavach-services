package dev.aparadhkavach.orchestration.controller;

import dev.aparadhkavach.orchestration.dto.FirTextSearchResource;
import dev.aparadhkavach.orchestration.dto.SimilarCaseResource;
import dev.aparadhkavach.orchestration.dto.SimilarCasesResource;
import dev.aparadhkavach.orchestration.search.model.FirTextSearchResult;
import dev.aparadhkavach.orchestration.search.model.SimilarCasesResult;
import dev.aparadhkavach.orchestration.search.service.SimilarCasesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Similar past cases (KSP #6 / Design & Schema §6.7). Stored-vector neighbors for FIR id; free-text
 * path embeds then ANN ({@code GET /v1/firs:search}). No Claude comparative essay (Auto/18).
 */
@RestController
public class SimilarCasesController {

  private static final Logger log = LoggerFactory.getLogger(SimilarCasesController.class);

  private final SimilarCasesService similarCasesService;

  public SimilarCasesController(SimilarCasesService similarCasesService) {
    this.similarCasesService = similarCasesService;
  }

  @GetMapping("/v1/firs/{firId}/similarCases")
  public SimilarCasesResource getSimilarCases(
      @PathVariable String firId, @RequestParam(required = false) Integer limit) {
    log.info("similarCases request firId={} limit={}", firId, limit);
    return toResource(similarCasesService.findSimilar(firId, limit));
  }

  /**
   * Typed narrative search — Voyage query embed + PgVector (Auto/18 free-text). Slash form is under
   * Gateway {@code /v1/firs/**} (no GW redeploy needed). Colon form kept as Auto/18 alias.
   */
  @GetMapping({"/v1/firs/search", "/v1/firs:search"})
  public FirTextSearchResource searchByText(
      @RequestParam("q") String q, @RequestParam(required = false) Integer limit) {
    log.info(
        "firs search request qChars={} limit={}",
        q == null ? 0 : q.trim().length(),
        limit);
    return toSearchResource(similarCasesService.searchByText(q, limit));
  }

  private static SimilarCasesResource toResource(SimilarCasesResult result) {
    return new SimilarCasesResource(
        result.firId(),
        result.limit(),
        result.similarCases().stream().map(SimilarCasesController::toCaseResource).toList());
  }

  private static FirTextSearchResource toSearchResource(FirTextSearchResult result) {
    return new FirTextSearchResource(
        result.query(),
        result.limit(),
        result.similarCases().stream().map(SimilarCasesController::toCaseResource).toList());
  }

  private static SimilarCaseResource toCaseResource(
      dev.aparadhkavach.orchestration.search.model.SimilarCase c) {
    return new SimilarCaseResource(
        c.firId(),
        c.similarityScore(),
        c.district(),
        c.crimeType(),
        c.dateFiled(),
        c.status());
  }
}

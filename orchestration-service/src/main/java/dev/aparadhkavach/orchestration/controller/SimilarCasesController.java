package dev.aparadhkavach.orchestration.controller;

import dev.aparadhkavach.orchestration.dto.SimilarCaseResource;
import dev.aparadhkavach.orchestration.dto.SimilarCasesResource;
import dev.aparadhkavach.orchestration.search.model.SimilarCasesResult;
import dev.aparadhkavach.orchestration.search.service.SimilarCasesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Similar past cases (KSP #6 / Design & Schema §6.7). Stored-vector PgVector neighbors only — no
 * Claude comparative essay (Auto/18 A11).
 */
@RestController
@RequestMapping("/v1/firs")
public class SimilarCasesController {

  private static final Logger log = LoggerFactory.getLogger(SimilarCasesController.class);

  private final SimilarCasesService similarCasesService;

  public SimilarCasesController(SimilarCasesService similarCasesService) {
    this.similarCasesService = similarCasesService;
  }

  @GetMapping("/{firId}/similarCases")
  public SimilarCasesResource getSimilarCases(
      @PathVariable String firId, @RequestParam(required = false) Integer limit) {
    log.info("similarCases request firId={} limit={}", firId, limit);
    return toResource(similarCasesService.findSimilar(firId, limit));
  }

  private static SimilarCasesResource toResource(SimilarCasesResult result) {
    return new SimilarCasesResource(
        result.firId(),
        result.limit(),
        result.similarCases().stream()
            .map(
                c ->
                    new SimilarCaseResource(
                        c.firId(),
                        c.similarityScore(),
                        c.district(),
                        c.crimeType(),
                        c.dateFiled(),
                        c.status()))
            .toList());
  }
}

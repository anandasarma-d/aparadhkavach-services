package dev.aparadhkavach.orchestration.dto;

import java.util.List;

/** Free-text similar-FIR search — ADR-020 JSON for {@code GET /v1/firs:search}. */
public record FirTextSearchResource(
    String query, int limit, List<SimilarCaseResource> similarCases) {}

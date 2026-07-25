package dev.aparadhkavach.orchestration.dto;

import java.util.List;

/** Similar-cases API resource — plain ADR-020 JSON; no Claude compare essay. */
public record SimilarCasesResource(
    String firId, int limit, List<SimilarCaseResource> similarCases) {}

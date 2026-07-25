package dev.aparadhkavach.orchestration.search.model;

import java.util.List;

/** Ranked similar FIRs for a probe {@code firId} (stored-vector path; no Voyage on read). */
public record SimilarCasesResult(String firId, int limit, List<SimilarCase> similarCases) {}

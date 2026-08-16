package dev.aparadhkavach.orchestration.search.model;

import java.util.List;

/** Ranked FIRs for a free-text narrative query (Voyage embed → PgVector ANN). */
public record FirTextSearchResult(String query, int limit, List<SimilarCase> similarCases) {}

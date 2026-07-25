package dev.aparadhkavach.orchestration.search.model;

import java.time.LocalDate;

/** One nearest-neighbor FIR from {@code fir_embeddings} (cosine similarity). */
public record SimilarCase(
    String firId,
    double similarityScore,
    String district,
    String crimeType,
    LocalDate dateFiled,
    String status) {}

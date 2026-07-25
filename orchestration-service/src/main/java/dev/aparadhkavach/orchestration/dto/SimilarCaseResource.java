package dev.aparadhkavach.orchestration.dto;

import java.time.LocalDate;

/** One ranked similar FIR (Auto/18). */
public record SimilarCaseResource(
    String firId,
    double similarityScore,
    String district,
    String crimeType,
    LocalDate dateFiled,
    String status) {}

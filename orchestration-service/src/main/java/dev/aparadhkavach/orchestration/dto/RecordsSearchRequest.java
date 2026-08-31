package dev.aparadhkavach.orchestration.dto;

/**
 * Plain-English discovery over FIR narratives (mvp2/20 K1 thin slice). Voyage embed → PgVector
 * ANN (same floor as typed Similar) → Claude citation envelope. Not an ACC-/FIR- seed ask.
 */
public record RecordsSearchRequest(String q, String conversationId, Integer limit) {}

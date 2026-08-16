package dev.aparadhkavach.orchestration.dto;

import java.util.List;

/**
 * Voice follow-up response (mvp2/12 Step H): same citation envelope as {@link QueryResource} plus
 * transcription metadata. Audio is never persisted (ADR-027).
 */
public record VoiceQueryResource(
    String queryId,
    String conversationId,
    String answer,
    List<String> evidenceSources,
    List<String> relatedFirs,
    List<RelatedEntityResource> relatedEntities,
    double confidenceScore,
    String reasoningSummary,
    long latencyMs,
    String transcription,
    double transcriptionConfidence,
    String transcriptionConfidenceTier,
    boolean needsConfirmation,
    String detectedLanguage) {

  public static VoiceQueryResource from(QueryResource query, TranscriptionMeta meta) {
    return new VoiceQueryResource(
        query.queryId(),
        query.conversationId(),
        query.answer(),
        query.evidenceSources(),
        query.relatedFirs(),
        query.relatedEntities(),
        query.confidenceScore(),
        query.reasoningSummary(),
        query.latencyMs(),
        meta.transcription(),
        meta.transcriptionConfidence(),
        meta.transcriptionConfidenceTier(),
        meta.needsConfirmation(),
        meta.detectedLanguage());
  }

  public record TranscriptionMeta(
      String transcription,
      double transcriptionConfidence,
      String transcriptionConfidenceTier,
      boolean needsConfirmation,
      String detectedLanguage) {}
}

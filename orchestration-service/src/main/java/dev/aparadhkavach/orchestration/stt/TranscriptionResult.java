package dev.aparadhkavach.orchestration.stt;

/** Result from internal STT microservice (ADR-027 — no audio retained). */
public record TranscriptionResult(
    String transcription,
    double confidence,
    String confidenceTier,
    boolean needsConfirmation,
    String detectedLanguage) {

  public static TranscriptionResult disabled() {
    return new TranscriptionResult("", 0.0, "low", true, "en");
  }

  public boolean isBlank() {
    return transcription == null || transcription.isBlank();
  }
}

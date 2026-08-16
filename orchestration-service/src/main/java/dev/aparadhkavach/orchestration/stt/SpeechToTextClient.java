package dev.aparadhkavach.orchestration.stt;

/** Orch → STT microservice (internal). Fail-soft callers map errors to 503. */
public interface SpeechToTextClient {

  TranscriptionResult transcribe(byte[] audioBytes, String filename, String languageHint);
}

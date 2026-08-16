package dev.aparadhkavach.orchestration.search.voyage;

/**
 * Voyage AI text embeddings (ADR-025 / mvp2 typed-similar). Corpus uses {@code voyage-3-large}
 * 1024-d document vectors; queries must use {@code input_type=query}.
 */
public interface VoyageEmbeddingClient {

  /**
   * Embed a single retrieval query. Returns a 1024-d float vector (model default).
   *
   * @throws dev.aparadhkavach.commons.exception.ValidationException if text is blank
   * @throws dev.aparadhkavach.commons.exception.ExternalServiceException on Voyage HTTP/parse failure
   */
  float[] embedQuery(String text);
}

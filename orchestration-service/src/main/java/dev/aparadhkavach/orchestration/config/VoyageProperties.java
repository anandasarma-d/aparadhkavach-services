package dev.aparadhkavach.orchestration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Voyage AI credentials (ADR-025). Bound under {@code aparadhkavach.voyage} — consumed by {@code
 * HttpVoyageEmbeddingClient} for typed-text similar (not under {@code spring.ai.*}).
 */
@Component
@ConfigurationProperties(prefix = "aparadhkavach.voyage")
public class VoyageProperties {

  private String apiKey;
  private String embeddingModel;

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getEmbeddingModel() {
    return embeddingModel;
  }

  public void setEmbeddingModel(String embeddingModel) {
    this.embeddingModel = embeddingModel;
  }
}

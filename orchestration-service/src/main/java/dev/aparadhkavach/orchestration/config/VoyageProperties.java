package dev.aparadhkavach.orchestration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Voyage AI credentials (ADR-025). No native Spring AI provider exists yet, so this isn't bound
 * under {@code spring.ai.*} — consumed by the custom {@code EmbeddingModel} wrapper once that Week
 * 1 business logic lands.
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

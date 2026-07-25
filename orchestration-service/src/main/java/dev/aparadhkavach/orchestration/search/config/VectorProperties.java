package dev.aparadhkavach.orchestration.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Semantic Search knobs (KSP #6 / A11). Default top-K from {@code VECTOR_TOP_K}; hard-cap for query
 * {@code limit} (Auto/18: default 5, max 10).
 */
@Component
@ConfigurationProperties(prefix = "aparadhkavach.vector")
public class VectorProperties {

  private int topK = 5;
  private int maxTopK = 10;

  public int getTopK() {
    return topK;
  }

  public void setTopK(int topK) {
    this.topK = topK;
  }

  public int getMaxTopK() {
    return maxTopK;
  }

  public void setMaxTopK(int maxTopK) {
    this.maxTopK = maxTopK;
  }
}

package dev.aparadhkavach.orchestration.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Semantic Search knobs (KSP #6 / A11). Default top-K from {@code VECTOR_TOP_K}; hard-cap for query
 * {@code limit} (Auto/18: default 5, max 10). Typed-text path applies {@link #textMinSimilarity}.
 */
@Component
@ConfigurationProperties(prefix = "aparadhkavach.vector")
public class VectorProperties {

  private int topK = 5;
  private int maxTopK = 10;

  /**
   * Minimum cosine similarity for typed-text ({@code /v1/firs/search}) neighbors. Below this, rows
   * are dropped so weak ANN hits (e.g. murder → robbery @ 0.40–0.49) do not mislead officers.
   * Default {@code 0.50}. FIR-id similarCases is unchanged. Env: {@code
   * VECTOR_TEXT_MIN_SIMILARITY}.
   */
  private double textMinSimilarity = 0.50;

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

  public double getTextMinSimilarity() {
    return textMinSimilarity;
  }

  public void setTextMinSimilarity(double textMinSimilarity) {
    this.textMinSimilarity = textMinSimilarity;
  }
}

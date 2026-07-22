package dev.aparadhkavach.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Risk-scoring presentation knobs (ADR-021 Part 3). */
@Component
@ConfigurationProperties(prefix = "aparadhkavach.risk")
public class RiskProperties {

  /** Section 7.5.1 — how many feature-importance entries to surface. */
  private int topFeatureCount = 3;

  public int getTopFeatureCount() {
    return topFeatureCount;
  }

  public void setTopFeatureCount(int topFeatureCount) {
    this.topFeatureCount = topFeatureCount;
  }
}

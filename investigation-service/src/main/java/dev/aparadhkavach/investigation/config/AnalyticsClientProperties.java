package dev.aparadhkavach.investigation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Internal Analytics Service base URL (ADR-021 Part 3). */
@Component
@ConfigurationProperties(prefix = "aparadhkavach.analytics")
public class AnalyticsClientProperties {

  private String baseUrl = "http://localhost:8082";

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }
}

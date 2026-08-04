package dev.aparadhkavach.orchestration.query.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Investigation Service base URL for Q&amp;A context (mvp2/11 — prefer Investigation HTTP). */
@Component
@ConfigurationProperties(prefix = "aparadhkavach.investigation")
public class InvestigationClientProperties {

  private String baseUrl = "http://localhost:8081";

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }
}

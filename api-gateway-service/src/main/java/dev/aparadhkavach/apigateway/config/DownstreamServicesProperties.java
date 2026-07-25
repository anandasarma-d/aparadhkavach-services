package dev.aparadhkavach.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Downstream service URLs — environment-driven (ADR-022). Distinct from the frontend's own env
 * vars; read server-side by the Gateway only.
 */
@Component
@ConfigurationProperties(prefix = "downstream")
public class DownstreamServicesProperties {

  private String investigationServiceUrl;
  private String analyticsServiceUrl;
  private String orchestrationServiceUrl;

  public String getInvestigationServiceUrl() {
    return investigationServiceUrl;
  }

  public void setInvestigationServiceUrl(String investigationServiceUrl) {
    this.investigationServiceUrl = investigationServiceUrl;
  }

  public String getAnalyticsServiceUrl() {
    return analyticsServiceUrl;
  }

  public void setAnalyticsServiceUrl(String analyticsServiceUrl) {
    this.analyticsServiceUrl = analyticsServiceUrl;
  }

  public String getOrchestrationServiceUrl() {
    return orchestrationServiceUrl;
  }

  public void setOrchestrationServiceUrl(String orchestrationServiceUrl) {
    this.orchestrationServiceUrl = orchestrationServiceUrl;
  }
}

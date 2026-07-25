package dev.aparadhkavach.apigateway.controller;

import dev.aparadhkavach.apigateway.config.DownstreamServicesProperties;
import dev.aparadhkavach.apigateway.proxy.DownstreamProxy;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single entry point for client requests (Section 6.1). Routes Feature 1 path prefixes to
 * downstream services — {@code /v1/accusedPersons/**} → Investigation, {@code /v1/analytics/**} →
 * Analytics, {@code /v1/entities/**} → Orchestration (K2), {@code /v1/firs/**} → Orchestration
 * (similar cases / A11). No JWT/RBAC here (out of scope for MVP-1).
 */
@RestController
public class GatewayController {

  private final DownstreamServicesProperties downstreamServices;
  private final DownstreamProxy downstreamProxy;

  public GatewayController(
      DownstreamServicesProperties downstreamServices, DownstreamProxy downstreamProxy) {
    this.downstreamServices = downstreamServices;
    this.downstreamProxy = downstreamProxy;
  }

  @RequestMapping("/v1/accusedPersons/**")
  public ResponseEntity<byte[]> routeToInvestigation(HttpServletRequest request)
      throws IOException {
    return downstreamProxy.forward(downstreamServices.getInvestigationServiceUrl(), request);
  }

  @RequestMapping("/v1/analytics/**")
  public ResponseEntity<byte[]> routeToAnalytics(HttpServletRequest request) throws IOException {
    return downstreamProxy.forward(downstreamServices.getAnalyticsServiceUrl(), request);
  }

  @RequestMapping("/v1/entities/**")
  public ResponseEntity<byte[]> routeToOrchestration(HttpServletRequest request)
      throws IOException {
    return downstreamProxy.forward(downstreamServices.getOrchestrationServiceUrl(), request);
  }

  @RequestMapping("/v1/firs/**")
  public ResponseEntity<byte[]> routeFirsToOrchestration(HttpServletRequest request)
      throws IOException {
    return downstreamProxy.forward(downstreamServices.getOrchestrationServiceUrl(), request);
  }
}

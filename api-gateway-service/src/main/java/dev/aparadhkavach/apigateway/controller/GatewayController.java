package dev.aparadhkavach.apigateway.controller;

import dev.aparadhkavach.apigateway.config.DownstreamServicesProperties;
import dev.aparadhkavach.apigateway.proxy.DownstreamProxy;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single entry point for client requests (Section 6.1). Routes Feature path prefixes to
 * downstream services. JWT/RBAC enforced by {@code JwtAuthFilter} (mvp2/10).
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

  @RequestMapping("/v1/auth/**")
  public ResponseEntity<byte[]> routeToAuth(HttpServletRequest request) throws IOException {
    return downstreamProxy.forward(downstreamServices.getAuthServiceUrl(), request);
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

  /** Typed-text similar FIRs (Auto/18 free-text / mvp2 D-111) — colon form; not under /v1/firs/. */
  @RequestMapping("/v1/firs:search")
  public ResponseEntity<byte[]> routeFirSearchToOrchestration(HttpServletRequest request)
      throws IOException {
    return downstreamProxy.forward(downstreamServices.getOrchestrationServiceUrl(), request);
  }

  /** F3 / mvp2/11 — custom method on queries (colon form; not under /v1/queries/). */
  @RequestMapping("/v1/queries:ask")
  public ResponseEntity<byte[]> routeAskToOrchestration(HttpServletRequest request)
      throws IOException {
    return downstreamProxy.forward(downstreamServices.getOrchestrationServiceUrl(), request);
  }

  /**
   * Voice ask (mvp2/12 Step H / D-115) — colon form under conversations. Explicit mapping mirrors
   * {@code queries:ask} so path matching cannot drop {@code :voice} on some containers.
   */
  @RequestMapping("/v1/conversations/*/queries:voice")
  public ResponseEntity<byte[]> routeVoiceToOrchestration(HttpServletRequest request)
      throws IOException {
    return downstreamProxy.forward(downstreamServices.getOrchestrationServiceUrl(), request);
  }

  /** Graph-RAC conversation store (mvp2/12 Step A). */
  @RequestMapping("/v1/conversations/**")
  public ResponseEntity<byte[]> routeConversationsToOrchestration(HttpServletRequest request)
      throws IOException {
    return downstreamProxy.forward(downstreamServices.getOrchestrationServiceUrl(), request);
  }
}

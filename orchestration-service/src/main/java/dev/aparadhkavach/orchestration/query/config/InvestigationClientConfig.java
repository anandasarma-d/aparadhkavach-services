package dev.aparadhkavach.orchestration.query.config;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Investigation HTTP client for Q&amp;A context. Hard timeouts stay under Catalyst's ~30s AppSail
 * budget (same pattern as {@code Neo4jQueryApiClient}) so a cold or mis-pointed Investigation never
 * surfaces as a Gateway 408 with silent Orch logs.
 */
@Configuration
public class InvestigationClientConfig {

  private static final Logger log = LoggerFactory.getLogger(InvestigationClientConfig.class);

  /** Connect budget — fail fast if Investigation URL is unreachable / blackholed. */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

  /**
   * Read budget — keep short. Soft-fail + Neo4j-only Claude is preferred over burning ~10s on an
   * Investigation cold start (that stack + Claude ~10s → Gateway/AppSail 408 around ~20s).
   */
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

  @Bean
  RestClient investigationRestClient(InvestigationClientProperties properties) {
    log.info(
        "Investigation RestClient baseUrl={} connectTimeout={}s readTimeout={}s",
        properties.getBaseUrl(),
        CONNECT_TIMEOUT.toSeconds(),
        READ_TIMEOUT.toSeconds());
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder()
        .baseUrl(properties.getBaseUrl())
        .requestFactory(requestFactory)
        .build();
  }
}

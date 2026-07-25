package dev.aparadhkavach.orchestration.graph.config;

import dev.aparadhkavach.orchestration.graph.http.Neo4jQueryApiClient;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Logs which Neo4j target, transport, and K2 build are actually live, then warms the Query API off
 * the request thread (D-059/D-060).
 */
@Configuration
public class GraphStartupProbe {

  /** Bump when changing the network query strategy — makes "is the new jar live?" answerable. */
  public static final String K2_BUILD_MARKER = "k2-http-no-bolt-v7";

  private static final Logger log = LoggerFactory.getLogger(GraphStartupProbe.class);

  @Bean
  ApplicationRunner neo4jWarmup(
      Neo4jQueryApiClient queryApiClient,
      @Value("${aparadhkavach.graph.transport:http}") String transport,
      @Value("${spring.neo4j.uri:unset}") String uri,
      @Value("${spring.neo4j.database:unset}") String database) {
    return args -> {
      log.info(
          "K2 graph build={} transport={} neo4jUri={} neo4jDatabase={} queryApiEndpoint={}",
          K2_BUILD_MARKER,
          transport,
          uri,
          database,
          queryApiClient.endpoint());
      if ("bolt".equalsIgnoreCase(transport)) {
        log.info(
            "neo4j warmup skipped (GRAPH_TRANSPORT=bolt; Driver autoconfig excluded on AppSail)");
        return;
      }
      CompletableFuture.runAsync(
          () -> {
            long t0 = System.nanoTime();
            try {
              queryApiClient.query("RETURN 1 AS ok", Map.of());
              log.info(
                  "neo4j warmup OK transport={} tookMs={}",
                  transport,
                  (System.nanoTime() - t0) / 1_000_000L);
            } catch (RuntimeException ex) {
              log.error(
                  "neo4j warmup FAILED transport={} tookMs={}: {}",
                  transport,
                  (System.nanoTime() - t0) / 1_000_000L,
                  ex.toString());
            }
          });
    };
  }
}

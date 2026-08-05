package dev.aparadhkavach.orchestration.graph.config;

import dev.aparadhkavach.orchestration.graph.service.EntityNetworkService;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Warms the real K2 network path (depth 1 + 2) after boot (D-063). Complements {@link
 * GraphStartupProbe}'s {@code RETURN 1} Query API ping.
 */
@Configuration
public class NetworkStartupProbe {

  private static final Logger log = LoggerFactory.getLogger(NetworkStartupProbe.class);

  @Bean
  ApplicationRunner networkPathWarmup(
      EntityNetworkService entityNetworkService,
      @Value("${aparadhkavach.graph.warmup-entity-id:ACC-00044}") String warmupEntityId) {
    return args ->
        CompletableFuture.runAsync(
            () -> {
              for (int depth : new int[] {1, 2}) {
                long t0 = System.nanoTime();
                try {
                  entityNetworkService.getNetwork(warmupEntityId, depth);
                  log.info(
                      "network path warmup OK entityId={} depth={} tookMs={}",
                      warmupEntityId,
                      depth,
                      (System.nanoTime() - t0) / 1_000_000L);
                } catch (RuntimeException ex) {
                  log.error(
                      "network path warmup FAILED entityId={} depth={} tookMs={}: {}",
                      warmupEntityId,
                      depth,
                      (System.nanoTime() - t0) / 1_000_000L,
                      ex.toString());
                }
              }
            });
  }
}

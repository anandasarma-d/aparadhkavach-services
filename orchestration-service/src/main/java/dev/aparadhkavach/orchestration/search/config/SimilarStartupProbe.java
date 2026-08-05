package dev.aparadhkavach.orchestration.search.config;

import dev.aparadhkavach.orchestration.search.service.SimilarCasesService;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Warms Hikari + Supabase PgVector ANN off the request thread (D-064). Cold {@code similarCases}
 * otherwise burns AppSail's ~30s budget on first JDBC (408). Neo4j has a parallel probe in
 * {@code GraphStartupProbe}.
 */
@Configuration
public class SimilarStartupProbe {

  private static final Logger log = LoggerFactory.getLogger(SimilarStartupProbe.class);

  @Bean
  ApplicationRunner similarCasesWarmup(
      SimilarCasesService similarCasesService,
      @Value("${aparadhkavach.vector.warmup-fir-id:FIR-003276}") String warmupFirId) {
    return args ->
        CompletableFuture.runAsync(
            () -> {
              long t0 = System.nanoTime();
              try {
                similarCasesService.findSimilar(warmupFirId, 5);
                log.info(
                    "similarCases warmup OK firId={} tookMs={}",
                    warmupFirId,
                    (System.nanoTime() - t0) / 1_000_000L);
              } catch (RuntimeException ex) {
                log.error(
                    "similarCases warmup FAILED firId={} tookMs={}: {}",
                    warmupFirId,
                    (System.nanoTime() - t0) / 1_000_000L,
                    ex.toString());
              }
            });
  }
}

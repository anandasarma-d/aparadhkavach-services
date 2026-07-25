package dev.aparadhkavach.orchestration.graph.config;

import java.util.concurrent.TimeUnit;
import org.neo4j.driver.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.neo4j.ConfigBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bolt timeouts for local {@code GRAPH_TRANSPORT=bolt} only. Not loaded on AppSail (HTTP Query API
 * default) — creating a Bolt {@code Driver} there blackholes cold-start (D-060).
 */
@Configuration
@ConditionalOnProperty(name = "aparadhkavach.graph.transport", havingValue = "bolt")
public class Neo4jDriverConfig {

  @Bean
  ConfigBuilderCustomizer aparadhkavachNeo4jTimeouts() {
    return (Config.ConfigBuilder builder) ->
        builder
            .withConnectionTimeout(8, TimeUnit.SECONDS)
            .withConnectionAcquisitionTimeout(10, TimeUnit.SECONDS)
            .withMaxTransactionRetryTime(6, TimeUnit.SECONDS);
  }
}

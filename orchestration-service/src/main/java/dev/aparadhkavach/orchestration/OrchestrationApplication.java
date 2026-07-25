package dev.aparadhkavach.orchestration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Orchestration entrypoint.
 *
 * <p>D-060: Neo4j Bolt autoconfig is excluded in {@code application.yml} (not via a runtime
 * listener — overwriting {@code spring.autoconfigure.exclude} as a CSV property source broke the
 * existing PgVectorStore exclude and crashed AppSail with Catalyst 503 "check startup command or
 * port"). HTTPS Query API is the default graph transport; set {@code GRAPH_TRANSPORT=bolt} only
 * when running locally with Neo4j excludes removed.
 */
// scanBasePackages reaches up to dev.aparadhkavach.commons so GlobalExceptionHandler
// (aparadhkavach-commons) is picked up — it lives outside this service's own package.
@SpringBootApplication(scanBasePackages = "dev.aparadhkavach")
public class OrchestrationApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrchestrationApplication.class, args);
  }
}

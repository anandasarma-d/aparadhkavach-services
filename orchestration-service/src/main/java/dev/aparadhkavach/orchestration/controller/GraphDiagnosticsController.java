package dev.aparadhkavach.orchestration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import dev.aparadhkavach.orchestration.graph.config.GraphStartupProbe;
import dev.aparadhkavach.orchestration.graph.http.Neo4jQueryApiClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Splits "can we reach Aura at all?" from "is the Cypher slow?" (D-060). HTTPS Query API only —
 * Bolt is excluded on AppSail.
 *
 * <p>Diagnostic only — reports config, never secrets. Remove after MVP-1 submission.
 */
@RestController
@RequestMapping("/v1/diagnostics")
public class GraphDiagnosticsController {

  private final Neo4jQueryApiClient queryApiClient;
  private final String transport;
  private final String uri;
  private final String database;

  public GraphDiagnosticsController(
      Neo4jQueryApiClient queryApiClient,
      @Value("${aparadhkavach.graph.transport:http}") String transport,
      @Value("${spring.neo4j.uri:unset}") String uri,
      @Value("${spring.neo4j.database:unset}") String database) {
    this.queryApiClient = queryApiClient;
    this.transport = transport;
    this.uri = uri;
    this.database = database;
  }

  @GetMapping("/neo4j")
  public ResponseEntity<Map<String, Object>> neo4j() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("build", GraphStartupProbe.K2_BUILD_MARKER);
    body.put("transport", transport);
    body.put("uri", uri);
    body.put("database", database);
    body.put("queryApiEndpoint", queryApiClient.endpoint());

    long t0 = System.nanoTime();
    try {
      queryApiClient.query("RETURN 1 AS ok", Map.of());
      body.put("pingMs", elapsedMs(t0));
    } catch (RuntimeException ex) {
      body.put("pingMs", elapsedMs(t0));
      body.put("stage", "ping");
      body.put("error", ex.toString());
      return ResponseEntity.status(502).body(body);
    }

    long t1 = System.nanoTime();
    try {
      JsonNode response =
          queryApiClient.query(
              "MATCH (a:Accused {accused_id: $entityId}) RETURN count(a) AS c",
              Map.of("entityId", "ACC-00124"));
      body.put("queryMs", elapsedMs(t1));
      body.put("acc00124Count", response.path("data").path("values").path(0).path(0).asLong());
      return ResponseEntity.ok(body);
    } catch (RuntimeException ex) {
      body.put("queryMs", elapsedMs(t1));
      body.put("stage", "query");
      body.put("error", ex.toString());
      return ResponseEntity.status(502).body(body);
    }
  }

  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }
}

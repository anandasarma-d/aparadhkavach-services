package dev.aparadhkavach.orchestration.graph.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Aura Query API client (D-060): {@code POST https://<host>/db/<database>/query/v2} with basic
 * auth. HTTPS 443 works from AppSail where Bolt 7687 silently hangs. Timeouts are hard-capped well
 * under Catalyst's ~30s budget so failures surface as readable errors, never a 408.
 *
 * <p>Important: Aura Free's user DB is often the instance id (e.g. {@code 601ef9b9}), not {@code
 * neo4j}. A wrong {@code NEO4J_DATABASE} yields HTTP 404 {@code DatabaseNotFound} — fix env, do not
 * chase Cypher.
 */
@Component
public class Neo4jQueryApiClient {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String endpoint;

  public Neo4jQueryApiClient(
      ObjectMapper objectMapper,
      @Value("${spring.neo4j.uri}") String neo4jUri,
      @Value("${spring.neo4j.database}") String database,
      @Value("${spring.neo4j.authentication.username}") String username,
      @Value("${spring.neo4j.authentication.password}") String password,
      @Value("${NEO4J_QUERY_API_URL:}") String endpointOverride) {
    this.objectMapper = objectMapper;
    this.endpoint =
        endpointOverride == null || endpointOverride.isBlank()
            ? deriveEndpoint(neo4jUri, database)
            : endpointOverride;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(15));
    this.restClient =
        RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(this.endpoint)
            .defaultHeaders(
                headers -> {
                  headers.setBasicAuth(username, password);
                  headers.setContentType(MediaType.APPLICATION_JSON);
                  headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
            .build();
  }

  /** {@code neo4j+s://601ef9b9.databases.neo4j.io} → {@code https://…/db/<db>/query/v2}. */
  static String deriveEndpoint(String neo4jUri, String database) {
    String host = neo4jUri.replaceFirst("^[a-zA-Z0-9+.\\-]+://", "");
    int slash = host.indexOf('/');
    if (slash >= 0) {
      host = host.substring(0, slash);
    }
    int colon = host.indexOf(':');
    if (colon >= 0) {
      host = host.substring(0, colon);
    }
    return "https://" + host + "/db/" + database + "/query/v2";
  }

  /**
   * Runs one Cypher statement; returns the raw Query API JSON (with {@code data.fields} / {@code
   * data.values}). Throws on transport errors, non-2xx, or in-body {@code errors}.
   */
  public JsonNode query(String statement, Map<String, Object> parameters) {
    Map<String, Object> body = Map.of("statement", statement, "parameters", parameters);
    return restClient
        .post()
        .body(body)
        .exchange(
            (request, response) -> {
              String raw = readBody(response);
              int status = response.getStatusCode().value();
              JsonNode json = parseJson(raw);
              if (status >= 400) {
                throw new IllegalStateException(
                    "Neo4j Query API HTTP "
                        + status
                        + " from "
                        + endpoint
                        + (json != null ? ": " + json : ": " + truncate(raw)));
              }
              if (json == null) {
                throw new IllegalStateException(
                    "Empty/non-JSON Query API response from " + endpoint + ": " + truncate(raw));
              }
              JsonNode errors = json.get("errors");
              if (errors != null && errors.isArray() && !errors.isEmpty()) {
                throw new IllegalStateException("Neo4j Query API errors: " + errors);
              }
              return json;
            });
  }

  public String endpoint() {
    return endpoint;
  }

  private JsonNode parseJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(raw);
    } catch (IOException ex) {
      return null;
    }
  }

  private static String readBody(ClientHttpResponse response) throws IOException {
    byte[] bytes = response.getBody().readAllBytes();
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static String truncate(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.length() <= 500 ? raw : raw.substring(0, 500) + "…";
  }
}

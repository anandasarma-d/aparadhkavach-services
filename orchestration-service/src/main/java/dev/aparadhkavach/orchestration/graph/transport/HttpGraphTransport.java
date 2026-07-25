package dev.aparadhkavach.orchestration.graph.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aparadhkavach.orchestration.graph.http.Neo4jQueryApiClient;
import dev.aparadhkavach.orchestration.graph.model.GraphNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default transport: Aura Query API over HTTPS 443 (D-060 — AppSail blocks Bolt 7687). */
@Component
@ConditionalOnProperty(
    name = "aparadhkavach.graph.transport",
    havingValue = "http",
    matchIfMissing = true)
public class HttpGraphTransport implements GraphQueryTransport {

  private static final TypeReference<Map<String, Object>> PROPERTIES_TYPE =
      new TypeReference<>() {};

  private final Neo4jQueryApiClient queryApiClient;
  private final ObjectMapper objectMapper;

  public HttpGraphTransport(Neo4jQueryApiClient queryApiClient, ObjectMapper objectMapper) {
    this.queryApiClient = queryApiClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<NetworkRow> fetchRows(String cypher, Map<String, Object> parameters) {
    JsonNode data = queryApiClient.query(cypher, parameters).path("data");
    Map<String, Integer> fieldIndex = new HashMap<>();
    JsonNode fields = data.path("fields");
    for (int i = 0; i < fields.size(); i++) {
      fieldIndex.put(fields.get(i).asText(), i);
    }

    List<NetworkRow> rows = new ArrayList<>();
    for (JsonNode row : data.path("values")) {
      rows.add(
          new NetworkRow(
              toGraphNode(value(row, fieldIndex, "start")),
              toGraphNode(value(row, fieldIndex, "fromNode")),
              toGraphNode(value(row, fieldIndex, "toNode")),
              text(value(row, fieldIndex, "relType"))));
    }
    return rows;
  }

  @Override
  public String name() {
    return "http";
  }

  private static JsonNode value(JsonNode row, Map<String, Integer> fieldIndex, String field) {
    Integer index = fieldIndex.get(field);
    return index == null ? null : row.get(index);
  }

  private GraphNode toGraphNode(JsonNode node) {
    if (node == null || node.isNull() || !node.isObject()) {
      return null;
    }
    List<String> labels = new ArrayList<>();
    for (JsonNode label : node.path("labels")) {
      labels.add(label.asText());
    }
    Map<String, Object> properties =
        objectMapper.convertValue(node.path("properties"), PROPERTIES_TYPE);
    if (properties == null) {
      properties = Map.of();
    }
    return new GraphNode(node.path("elementId").asText(), labels, properties);
  }

  private static String text(JsonNode node) {
    return node == null || node.isNull() ? null : node.asText();
  }
}

package dev.aparadhkavach.orchestration.graph.transport;

import dev.aparadhkavach.orchestration.graph.model.GraphNode;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.types.Node;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

/**
 * Bolt transport via {@link Neo4jClient} — local/dev only ({@code GRAPH_TRANSPORT=bolt}). Not the
 * AppSail default: outbound 7687 hangs there (D-060).
 */
@Component
@ConditionalOnProperty(name = "aparadhkavach.graph.transport", havingValue = "bolt")
public class BoltGraphTransport implements GraphQueryTransport {

  private final Neo4jClient neo4jClient;

  public BoltGraphTransport(Neo4jClient neo4jClient) {
    this.neo4jClient = neo4jClient;
  }

  @Override
  public List<NetworkRow> fetchRows(String cypher, Map<String, Object> parameters) {
    return List.copyOf(
        neo4jClient
            .query(cypher)
            .bindAll(parameters)
            .fetchAs(NetworkRow.class)
            .mappedBy(
                (typeSystem, record) ->
                    new NetworkRow(
                        toGraphNode(record.get("start").asNode()),
                        record.get("fromNode").isNull()
                            ? null
                            : toGraphNode(record.get("fromNode").asNode()),
                        record.get("toNode").isNull()
                            ? null
                            : toGraphNode(record.get("toNode").asNode()),
                        record.get("relType").isNull() ? null : record.get("relType").asString()))
            .all());
  }

  @Override
  public String name() {
    return "bolt";
  }

  private static GraphNode toGraphNode(Node node) {
    List<String> labels = new java.util.ArrayList<>();
    node.labels().forEach(labels::add);
    return new GraphNode(node.elementId(), labels, node.asMap());
  }
}

package dev.aparadhkavach.orchestration.graph.transport;

import dev.aparadhkavach.orchestration.graph.model.GraphNode;
import java.util.List;
import java.util.Map;

/**
 * How network Cypher reaches Aura (D-060). {@code http} (default) uses the Aura Query API over
 * HTTPS 443 — AppSail blocks/blackholes outbound Bolt 7687, which hung requests until Catalyst's
 * ~30s {@code EXECUTION_TIME_EXCEEDED}. {@code bolt} remains for local/dev via {@code
 * GRAPH_TRANSPORT=bolt}.
 */
public interface GraphQueryTransport {

  /** Runs network Cypher expected to return {@code start, fromNode, toNode, relType} rows. */
  List<NetworkRow> fetchRows(String cypher, Map<String, Object> parameters);

  String name();

  /** {@code from}/{@code to}/{@code relType} are null for zero-degree start rows. */
  record NetworkRow(GraphNode start, GraphNode from, GraphNode to, String relType) {}
}

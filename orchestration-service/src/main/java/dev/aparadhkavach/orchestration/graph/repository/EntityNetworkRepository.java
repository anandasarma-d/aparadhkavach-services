package dev.aparadhkavach.orchestration.graph.repository;

import dev.aparadhkavach.commons.error.ErrorCode;
import dev.aparadhkavach.commons.exception.GraphTraversalException;
import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import dev.aparadhkavach.orchestration.graph.model.GraphEntityKind;
import dev.aparadhkavach.orchestration.graph.model.GraphNodeMapper;
import dev.aparadhkavach.orchestration.graph.model.NetworkEdge;
import dev.aparadhkavach.orchestration.graph.model.NetworkNode;
import dev.aparadhkavach.orchestration.graph.queries.CypherQueryLibrary;
import dev.aparadhkavach.orchestration.graph.transport.GraphQueryTransport;
import dev.aparadhkavach.orchestration.graph.transport.GraphQueryTransport.NetworkRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Neo4j neighborhood reads for K2. Transport-agnostic (Bolt or Aura Query API — D-060); one
 * round-trip per request (start + edges) to stay under AppSail's ~30s budget (D-059).
 */
@Repository
public class EntityNetworkRepository {

  private static final Logger log = LoggerFactory.getLogger(EntityNetworkRepository.class);

  private final GraphQueryTransport transport;

  public EntityNetworkRepository(GraphQueryTransport transport) {
    this.transport = transport;
  }

  /**
   * Loads start node + undirected neighborhood in one query. Throws {@link
   * ResourceNotFoundException} when no matching node exists.
   */
  public NetworkBundle fetchNetwork(String entityId, int depth, int edgeLimit) {
    GraphEntityKind kind = GraphEntityKind.fromEntityId(entityId);
    String cypher;
    if (kind != null) {
      cypher =
          depth >= 2
              ? CypherQueryLibrary.networkDepth2ByKind(kind)
              : CypherQueryLibrary.networkDepth1ByKind(kind);
    } else {
      cypher = CypherQueryLibrary.NETWORK_DEPTH_1_UNION;
    }

    long t0 = System.nanoTime();
    try {
      List<NetworkRow> rows =
          transport.fetchRows(cypher, Map.of("entityId", entityId, "edgeLimit", edgeLimit));

      if (rows.isEmpty() || rows.get(0).start() == null) {
        throw new ResourceNotFoundException("No graph entity for entityId=" + entityId);
      }

      NetworkNode start = GraphNodeMapper.toNetworkNode(rows.get(0).start());
      Map<String, NetworkNode> nodes = new LinkedHashMap<>();
      nodes.put(start.id(), start);
      Set<String> edgeKeys = new LinkedHashSet<>();
      List<NetworkEdge> edges = new ArrayList<>();

      for (NetworkRow row : rows) {
        if (row.from() == null || row.to() == null || row.relType() == null) {
          continue;
        }
        NetworkNode from = GraphNodeMapper.toNetworkNode(row.from());
        NetworkNode to = GraphNodeMapper.toNetworkNode(row.to());
        nodes.putIfAbsent(from.id(), from);
        nodes.putIfAbsent(to.id(), to);
        String key = from.id() + "|" + row.relType() + "|" + to.id();
        if (edgeKeys.add(key)) {
          edges.add(new NetworkEdge(from.id(), to.id(), row.relType()));
        }
      }

      log.info(
          "neo4j network entityId={} depth={} kind={} transport={} nodes={} edges={} tookMs={}",
          entityId,
          depth,
          kind != null ? kind.name() : "UNION",
          transport.name(),
          nodes.size(),
          edges.size(),
          (System.nanoTime() - t0) / 1_000_000L);
      return new NetworkBundle(start, List.copyOf(nodes.values()), List.copyOf(edges));
    } catch (ResourceNotFoundException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      log.error(
          "neo4j network FAILED entityId={} depth={} kind={} transport={} tookMs={}: {}",
          entityId,
          depth,
          kind != null ? kind.name() : "UNION",
          transport.name(),
          (System.nanoTime() - t0) / 1_000_000L,
          ex.toString());
      throw new GraphTraversalException(
          ErrorCode.GRAPH_TRAVERSAL_FAILED,
          "Neo4j neighborhood traversal failed for entityId=" + entityId + ": " + ex.getMessage());
    }
  }

  public record NetworkBundle(
      NetworkNode start, List<NetworkNode> nodes, List<NetworkEdge> edges) {}
}

package dev.aparadhkavach.orchestration.graph.service;

import dev.aparadhkavach.commons.datastore.EntityIdFormat;
import dev.aparadhkavach.orchestration.graph.config.GraphProperties;
import dev.aparadhkavach.orchestration.graph.model.EntityNetwork;
import dev.aparadhkavach.orchestration.graph.model.NetworkEdge;
import dev.aparadhkavach.orchestration.graph.model.NetworkNode;
import dev.aparadhkavach.orchestration.graph.repository.EntityNetworkRepository;
import dev.aparadhkavach.orchestration.graph.repository.EntityNetworkRepository.NetworkBundle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * K2 criminal-network neighborhood (A9). Clamps depth to {@code [1, networkMaxDepth]}, caps nodes,
 * and never invents graph data.
 */
@Service
public class EntityNetworkService {

  private final EntityNetworkRepository repository;
  private final GraphProperties graphProperties;

  public EntityNetworkService(EntityNetworkRepository repository, GraphProperties graphProperties) {
    this.repository = repository;
    this.graphProperties = graphProperties;
  }

  public EntityNetwork getNetwork(String rawEntityId, Integer requestedDepth) {
    String entityId = EntityIdFormat.requireValid(rawEntityId);
    int depth = clampDepth(requestedDepth);

    // Fetch enough edges that node capping in Java remains meaningful after Cypher LIMIT.
    int edgeLimit = Math.max(graphProperties.getNetworkMaxNodes() * 4, 100);
    NetworkBundle bundle = repository.fetchNetwork(entityId, depth, edgeLimit);

    NetworkNode start = bundle.start();
    List<NetworkNode> nodes = new ArrayList<>();
    nodes.add(start);
    for (NetworkNode node : bundle.nodes()) {
      if (!node.id().equals(start.id())) {
        nodes.add(node);
      }
    }

    boolean truncated = false;
    int maxNodes = graphProperties.getNetworkMaxNodes();
    if (nodes.size() > maxNodes) {
      truncated = true;
      nodes = List.copyOf(nodes.subList(0, maxNodes));
    }

    Set<String> allowed = new LinkedHashSet<>();
    for (NetworkNode node : nodes) {
      allowed.add(node.id());
    }
    List<NetworkEdge> edges = new ArrayList<>();
    for (NetworkEdge edge : bundle.edges()) {
      if (allowed.contains(edge.from()) && allowed.contains(edge.to())) {
        edges.add(edge);
      }
    }

    return new EntityNetwork(
        entityId, start.label(), depth, List.copyOf(nodes), List.copyOf(edges), truncated);
  }

  int clampDepth(Integer requestedDepth) {
    int max = Math.max(1, graphProperties.getNetworkMaxDepth());
    int fallback = Math.min(Math.max(1, graphProperties.getNetworkDefaultDepth()), max);
    if (requestedDepth == null) {
      return fallback;
    }
    if (requestedDepth < 1) {
      return 1;
    }
    return Math.min(requestedDepth, max);
  }
}

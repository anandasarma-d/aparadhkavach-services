package dev.aparadhkavach.orchestration.graph.model;

import java.util.List;

/** Internal neighborhood result before DTO mapping (includes truncation flag). */
public record EntityNetwork(
    String entityId,
    String entityLabel,
    int depth,
    List<NetworkNode> nodes,
    List<NetworkEdge> edges,
    boolean truncated) {}

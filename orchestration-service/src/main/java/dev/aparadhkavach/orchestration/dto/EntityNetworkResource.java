package dev.aparadhkavach.orchestration.dto;

import java.util.List;

/** K2 network API resource — flat shape per Auto/17 (prototype); no Claude summary. */
public record EntityNetworkResource(
    String entityId,
    String entityLabel,
    int depth,
    List<NetworkNodeResource> nodes,
    List<NetworkEdgeResource> edges,
    boolean truncated) {}

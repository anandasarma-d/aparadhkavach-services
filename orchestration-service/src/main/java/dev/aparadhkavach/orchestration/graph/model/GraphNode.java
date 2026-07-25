package dev.aparadhkavach.orchestration.graph.model;

import java.util.List;
import java.util.Map;

/**
 * Transport-neutral node view (D-060). Bolt {@code org.neo4j.driver.types.Node} and Aura Query API
 * JSON nodes ({@code {elementId, labels, properties}}) both collapse to this before DTO mapping.
 */
public record GraphNode(String elementId, List<String> labels, Map<String, Object> properties) {}

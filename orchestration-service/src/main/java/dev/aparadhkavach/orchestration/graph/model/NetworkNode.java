package dev.aparadhkavach.orchestration.graph.model;

/**
 * One graph node in a K2 neighborhood response. {@code type} is the primary Neo4j label (e.g.
 * Accused, FIR).
 */
public record NetworkNode(String id, String type, String label) {}

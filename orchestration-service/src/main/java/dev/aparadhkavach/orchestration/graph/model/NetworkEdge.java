package dev.aparadhkavach.orchestration.graph.model;

/** One directed Neo4j relationship edge in a K2 neighborhood response. */
public record NetworkEdge(String from, String to, String type) {}

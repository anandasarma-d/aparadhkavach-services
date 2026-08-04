package dev.aparadhkavach.orchestration.query.model;

/**
 * Resolved ask seed after validation — exactly one of accused / FIR (mvp2/11).
 *
 * @param kind whether {@code entityId} is an accused or FIR
 * @param entityId validated Neo4j / Investigation id
 */
public record QuerySeed(QuerySeedKind kind, String entityId) {}

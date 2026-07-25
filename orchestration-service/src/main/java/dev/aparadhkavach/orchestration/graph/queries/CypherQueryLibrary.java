package dev.aparadhkavach.orchestration.graph.queries;

import dev.aparadhkavach.orchestration.graph.model.GraphEntityKind;

/**
 * Parameterised Cypher for Graph Intelligence (Section 6.5 / Auto/17 A9).
 *
 * <p>Prefix-routed queries interpolate a single label + id property (Section 5.4 index seek). One
 * round-trip returns the start node and neighborhood edges so AppSail does not pay Bolt connect
 * cost twice under the ~30s execution budget (D-059).
 */
public final class CypherQueryLibrary {
  private CypherQueryLibrary() {}

  public static String networkDepth1ByKind(GraphEntityKind kind) {
    // OPTIONAL MATCH keeps the start row when degree = 0.
    return """
        MATCH (start:%s {%s: $entityId})
        OPTIONAL MATCH (start)-[rel]-(other)
        WHERE rel IS NULL OR NOT other:CrimeType
        RETURN start AS start,
               startNode(rel) AS fromNode,
               endNode(rel) AS toNode,
               type(rel) AS relType
        LIMIT $edgeLimit
        """
        .formatted(kind.label(), kind.idProperty());
  }

  public static String networkDepth2ByKind(GraphEntityKind kind) {
    return """
        MATCH (start:%s {%s: $entityId})
        CALL {
          WITH start
          OPTIONAL MATCH (start)-[rel]-(other)
          WHERE rel IS NULL OR NOT other:CrimeType
          RETURN startNode(rel) AS fromNode, endNode(rel) AS toNode, type(rel) AS relType
          UNION
          WITH start
          MATCH (start)-[r1]-(mid)-[r2]-(other)
          WHERE mid <> other
            AND start <> other
            AND NOT mid:CrimeType
            AND NOT other:CrimeType
          RETURN startNode(r1) AS fromNode, endNode(r1) AS toNode, type(r1) AS relType
          UNION
          WITH start
          MATCH (start)-[r1]-(mid)-[r2]-(other)
          WHERE mid <> other
            AND start <> other
            AND NOT mid:CrimeType
            AND NOT other:CrimeType
          RETURN startNode(r2) AS fromNode, endNode(r2) AS toNode, type(r2) AS relType
        }
        RETURN start AS start, fromNode, toNode, relType
        LIMIT $edgeLimit
        """
        .formatted(kind.label(), kind.idProperty());
  }

  /** Fallback when id prefix is unknown — slower; avoid on the demo path. */
  public static final String NETWORK_DEPTH_1_UNION =
      """
      CALL {
        MATCH (n:Accused {accused_id: $entityId}) RETURN n AS start
        UNION
        MATCH (n:FIR {fir_id: $entityId}) RETURN n AS start
        UNION
        MATCH (n:Victim {victim_id: $entityId}) RETURN n AS start
        UNION
        MATCH (n:Witness {witness_id: $entityId}) RETURN n AS start
        UNION
        MATCH (n:Location {location_id: $entityId}) RETURN n AS start
        UNION
        MATCH (n:Vehicle {vehicle_id: $entityId}) RETURN n AS start
        UNION
        MATCH (n:PhoneNumber {phone_id: $entityId}) RETURN n AS start
        UNION
        MATCH (n:InvestigationOfficer {officer_id: $entityId}) RETURN n AS start
        UNION
        MATCH (n:CrimeType {type_id: $entityId}) RETURN n AS start
      }
      WITH start
      LIMIT 1
      OPTIONAL MATCH (start)-[rel]-(other)
      WHERE rel IS NULL OR NOT other:CrimeType
      RETURN start AS start,
             startNode(rel) AS fromNode,
             endNode(rel) AS toNode,
             type(rel) AS relType
      LIMIT $edgeLimit
      """;
}

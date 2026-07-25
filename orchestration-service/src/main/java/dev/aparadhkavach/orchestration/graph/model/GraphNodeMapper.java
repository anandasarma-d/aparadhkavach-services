package dev.aparadhkavach.orchestration.graph.model;

import java.util.List;
import java.util.Map;

/** Maps {@link GraphNode} values to network DTOs using Section 5.4 property names. */
public final class GraphNodeMapper {
  private GraphNodeMapper() {}

  private static final List<String> ID_KEYS =
      List.of(
          "accused_id",
          "fir_id",
          "victim_id",
          "witness_id",
          "location_id",
          "vehicle_id",
          "phone_id",
          "officer_id",
          "type_id");

  public static NetworkNode toNetworkNode(GraphNode node) {
    String type = primaryLabel(node);
    String id = resolveId(node);
    return new NetworkNode(id, type, resolveLabel(node, type, id));
  }

  public static String resolveId(GraphNode node) {
    Map<String, Object> properties = node.properties();
    for (String key : ID_KEYS) {
      Object value = properties.get(key);
      if (value != null) {
        return String.valueOf(value);
      }
    }
    return node.elementId();
  }

  static String primaryLabel(GraphNode node) {
    return node.labels().isEmpty() ? "Unknown" : node.labels().get(0);
  }

  static String resolveLabel(GraphNode node, String type, String id) {
    return switch (type) {
      case "Accused", "Victim", "Witness", "InvestigationOfficer" -> stringOr(node, "name", id);
      case "FIR" -> stringOr(node, "fir_id", stringOr(node, "fir_number", id));
      case "Location" -> {
        String district = stringOr(node, "district", "");
        String locationType = stringOr(node, "location_type", "");
        String combined = (district + " " + locationType).trim();
        yield combined.isEmpty() ? id : combined;
      }
      case "Vehicle" -> stringOr(node, "registration_number", id);
      case "PhoneNumber" -> stringOr(node, "number", id);
      case "CrimeType" -> stringOr(node, "category", id);
      default -> id;
    };
  }

  private static String stringOr(GraphNode node, String key, String fallback) {
    Object value = node.properties().get(key);
    if (value == null) {
      return fallback;
    }
    String text = String.valueOf(value);
    return text.isBlank() ? fallback : text;
  }
}

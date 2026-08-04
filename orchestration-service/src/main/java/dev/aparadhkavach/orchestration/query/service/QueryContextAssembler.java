package dev.aparadhkavach.orchestration.query.service;

import static dev.aparadhkavach.orchestration.query.QueryContextConstants.EDGES_HEADER;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.EDGE_PREFIX;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.EDGE_REL_CLOSE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.EDGE_REL_OPEN;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.FIR_ID_PREFIX;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.FIR_NODE_TYPE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.GRAPH_DEPTH;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.GRAPH_TRUNCATED;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.MISSING_VALUE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.NODES_HEADER;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.NODE_LABEL;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.NODE_PREFIX;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.NODE_TYPE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_ACCUSED_ID;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_ADDRESS_DISTRICT_ID;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_NAME;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_PRIOR_OFFENSE_COUNT;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_PROFILE_HEADER;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_PROFILE_UNAVAILABLE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_SCORE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.SEED_ID;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.SEED_KIND;

import dev.aparadhkavach.orchestration.graph.model.EntityNetwork;
import dev.aparadhkavach.orchestration.graph.model.NetworkEdge;
import dev.aparadhkavach.orchestration.graph.model.NetworkNode;
import dev.aparadhkavach.orchestration.query.client.InvestigationRiskProfileSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Builds the textual CONTEXT block for Claude (Investigation + 1-hop graph neighborhood). */
final class QueryContextAssembler {

  private QueryContextAssembler() {}

  static String assemble(
      String seedKind,
      String seedId,
      Optional<InvestigationRiskProfileSnapshot> risk,
      EntityNetwork network) {
    StringBuilder sb = new StringBuilder(1024);
    sb.append(SEED_KIND).append(seedKind).append('\n');
    sb.append(SEED_ID).append(seedId).append('\n');

    if (risk.isPresent()) {
      InvestigationRiskProfileSnapshot r = risk.get();
      sb.append(RISK_PROFILE_HEADER);
      sb.append(RISK_ACCUSED_ID).append(nullToDash(r.accusedId())).append('\n');
      sb.append(RISK_NAME).append(nullToDash(r.name())).append('\n');
      sb.append(RISK_ADDRESS_DISTRICT_ID).append(nullToDash(r.addressDistrictId())).append('\n');
      sb.append(RISK_PRIOR_OFFENSE_COUNT).append(r.priorOffenseCount()).append('\n');
      sb.append(RISK_SCORE).append(r.riskScore()).append('\n');
    } else {
      sb.append(RISK_PROFILE_UNAVAILABLE);
    }

    sb.append(GRAPH_DEPTH).append(network.depth()).append('\n');
    sb.append(GRAPH_TRUNCATED).append(network.truncated()).append('\n');
    sb.append(NODES_HEADER);
    for (NetworkNode node : network.nodes()) {
      sb.append(NODE_PREFIX)
          .append(node.id())
          .append(NODE_TYPE)
          .append(node.type())
          .append(NODE_LABEL)
          .append(nullToDash(node.label()))
          .append('\n');
    }
    sb.append(EDGES_HEADER);
    for (NetworkEdge edge : network.edges()) {
      sb.append(EDGE_PREFIX)
          .append(edge.from())
          .append(EDGE_REL_OPEN)
          .append(edge.type())
          .append(EDGE_REL_CLOSE)
          .append(edge.to())
          .append('\n');
    }
    return sb.toString();
  }

  static List<String> defaultEvidence(
      String seedId, Optional<InvestigationRiskProfileSnapshot> risk, EntityNetwork network) {
    Set<String> ids = new LinkedHashSet<>();
    ids.add(seedId);
    risk.map(InvestigationRiskProfileSnapshot::accusedId).ifPresent(ids::add);
    for (NetworkNode node : network.nodes()) {
      ids.add(node.id());
    }
    return List.copyOf(ids);
  }

  static List<String> firIdsFromNetwork(EntityNetwork network) {
    List<String> firs = new ArrayList<>();
    for (NetworkNode node : network.nodes()) {
      if (node.id() != null && node.id().toUpperCase().startsWith(FIR_ID_PREFIX)) {
        firs.add(node.id());
      } else if (FIR_NODE_TYPE.equalsIgnoreCase(node.type())) {
        firs.add(node.id());
      }
    }
    return List.copyOf(firs);
  }

  private static String nullToDash(String value) {
    return value == null || value.isBlank() ? MISSING_VALUE : value;
  }
}

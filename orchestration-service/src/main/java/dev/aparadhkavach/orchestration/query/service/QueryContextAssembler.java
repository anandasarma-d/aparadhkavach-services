package dev.aparadhkavach.orchestration.query.service;

import static dev.aparadhkavach.orchestration.query.QueryContextConstants.EDGE_PREFIX;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.EDGE_REL_CLOSE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.EDGE_REL_OPEN;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.EDGES_HEADER;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.FIR_ID_PREFIX;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.FIR_NODE_TYPE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.GRAPH_DEPTH;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.GRAPH_TRUNCATED;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.HIT_CRIME;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.HIT_DISTRICT;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.HIT_FILED;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.HIT_PREFIX;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.HIT_SCORE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.HIT_STATUS;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.MISSING_VALUE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.NODE_LABEL;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.NODE_PREFIX;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.NODE_TYPE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.NODES_HEADER;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.OFFICER_QUESTION;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.PROBE_FIR;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RETRIEVAL_MODE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RETRIEVAL_RECORDS_NL;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RETRIEVAL_SIMILAR;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_ACCUSED_ID;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_ADDRESS_DISTRICT_ID;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_NAME;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_PRIOR_OFFENSE_COUNT;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_PROFILE_HEADER;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_PROFILE_UNAVAILABLE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.RISK_SCORE;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.SEED_ID;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.SEED_KIND;
import static dev.aparadhkavach.orchestration.query.QueryContextConstants.SIMILAR_HITS_HEADER;

import dev.aparadhkavach.orchestration.graph.model.EntityNetwork;
import dev.aparadhkavach.orchestration.graph.model.NetworkEdge;
import dev.aparadhkavach.orchestration.graph.model.NetworkNode;
import dev.aparadhkavach.orchestration.query.client.InvestigationRiskProfileSnapshot;
import dev.aparadhkavach.orchestration.search.model.SimilarCase;
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

  /** mvp2/12 Step F — ranked PgVector neighbors only (no Neo4j / Investigation pack). */
  static String assembleSimilar(
      String probeFirId, List<SimilarCase> hits, String officerQuestion) {
    StringBuilder sb = new StringBuilder(1024);
    sb.append(RETRIEVAL_MODE).append(RETRIEVAL_SIMILAR).append('\n');
    sb.append(SEED_KIND).append(RETRIEVAL_SIMILAR).append('\n');
    sb.append(SEED_ID).append(probeFirId).append('\n');
    sb.append(PROBE_FIR).append(probeFirId).append('\n');
    if (officerQuestion != null && !officerQuestion.isBlank()) {
      sb.append(OFFICER_QUESTION).append(officerQuestion.trim()).append('\n');
    }
    appendSimilarHits(sb, hits);
    return sb.toString();
  }

  /**
   * mvp2/20 — open NL discovery: officer question + ANN hits (no probe FIR / Neo4j). Same hit
   * rows as typed Similar; Claude writes the conversational envelope.
   */
  static String assembleRecordsNl(String officerQuestion, List<SimilarCase> hits) {
    StringBuilder sb = new StringBuilder(1024);
    sb.append(RETRIEVAL_MODE).append(RETRIEVAL_RECORDS_NL).append('\n');
    sb.append(SEED_KIND).append(RETRIEVAL_RECORDS_NL).append('\n');
    sb.append(SEED_ID).append("TEXT_QUERY").append('\n');
    if (officerQuestion != null && !officerQuestion.isBlank()) {
      sb.append(OFFICER_QUESTION).append(officerQuestion.trim()).append('\n');
    }
    appendSimilarHits(sb, hits);
    return sb.toString();
  }

  private static void appendSimilarHits(StringBuilder sb, List<SimilarCase> hits) {
    sb.append(SIMILAR_HITS_HEADER);
    if (hits == null || hits.isEmpty()) {
      sb.append("  (none)\n");
      return;
    }
    for (SimilarCase hit : hits) {
      sb.append(HIT_PREFIX)
          .append(nullToDash(hit.firId()))
          .append(HIT_SCORE)
          .append(String.format(java.util.Locale.ROOT, "%.3f", hit.similarityScore()))
          .append(HIT_DISTRICT)
          .append(nullToDash(hit.district()))
          .append(HIT_CRIME)
          .append(nullToDash(hit.crimeType()))
          .append(HIT_FILED)
          .append(hit.dateFiled() == null ? MISSING_VALUE : hit.dateFiled().toString())
          .append(HIT_STATUS)
          .append(nullToDash(hit.status()))
          .append('\n');
    }
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

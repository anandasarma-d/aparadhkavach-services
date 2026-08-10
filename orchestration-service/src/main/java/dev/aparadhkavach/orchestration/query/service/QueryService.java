package dev.aparadhkavach.orchestration.query.service;

import dev.aparadhkavach.commons.datastore.EntityIdFormat;
import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.dto.QueryRequest;
import dev.aparadhkavach.orchestration.dto.QueryResource;
import dev.aparadhkavach.orchestration.dto.RelatedEntityResource;
import dev.aparadhkavach.orchestration.graph.model.EntityNetwork;
import dev.aparadhkavach.orchestration.graph.model.NetworkNode;
import dev.aparadhkavach.orchestration.graph.service.EntityNetworkService;
import dev.aparadhkavach.orchestration.query.client.InvestigationRiskProfileClient;
import dev.aparadhkavach.orchestration.query.client.InvestigationRiskProfileSnapshot;
import dev.aparadhkavach.orchestration.query.model.QuerySeed;
import dev.aparadhkavach.orchestration.query.model.QuerySeedKind;
import dev.aparadhkavach.orchestration.query.service.ClaudeQueryBridge.AskTask;
import dev.aparadhkavach.orchestration.query.service.ClaudeQueryBridge.ClaudeAnswer;
import dev.aparadhkavach.orchestration.query.service.ClaudeQueryBridge.RelatedEntity;
import dev.aparadhkavach.orchestration.search.model.SimilarCase;
import dev.aparadhkavach.orchestration.search.model.SimilarCasesResult;
import dev.aparadhkavach.orchestration.search.service.SimilarCasesService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * F3 / mvp2/11 ask path: Investigation risk (when accused) + Neo4j depth <strong>1</strong> only →
 * one Claude call → conversational envelope. Conversation persistence is owned by {@link
 * ConversationService} (mvp2/12 Step A). Step F similar-cases asks use {@link #askSimilar}.
 */
@Service
public class QueryService {

  private static final Logger log = LoggerFactory.getLogger(QueryService.class);

  /** Hard-cap for this endpoint — ignore GRAPH_NETWORK_DEFAULT_DEPTH / traversal depth 3. */
  public static final int QUERY_GRAPH_DEPTH = 1;

  private final EntityNetworkService entityNetworkService;
  private final InvestigationRiskProfileClient investigationRiskProfileClient;
  private final ClaudeQueryBridge claudeQueryBridge;
  private final SimilarCasesService similarCasesService;

  public QueryService(
      EntityNetworkService entityNetworkService,
      InvestigationRiskProfileClient investigationRiskProfileClient,
      ClaudeQueryBridge claudeQueryBridge,
      SimilarCasesService similarCasesService) {
    this.entityNetworkService = entityNetworkService;
    this.investigationRiskProfileClient = investigationRiskProfileClient;
    this.claudeQueryBridge = claudeQueryBridge;
    this.similarCasesService = similarCasesService;
  }

  /** Single-shot ask used by tests / internal callers — no conversation store. */
  public QueryResource ask(QueryRequest request) {
    return ask(request, null);
  }

  /**
   * @param conversationId real thread id from {@link ConversationService}, or {@code null} for a
   *     one-off envelope (legacy sentinel avoided — callers should prefer ConversationService)
   */
  public QueryResource ask(QueryRequest request, String conversationId) {
    return ask(request, conversationId, null);
  }

  /**
   * @param priorTurnsBlock packed PRIOR_TURNS from {@link ConversationHistoryPacker} (mvp2/12 Step
   *     D), or null/blank for single-shot
   */
  public QueryResource ask(QueryRequest request, String conversationId, String priorTurnsBlock) {
    long started = System.currentTimeMillis();
    QuerySeed seed = resolveSeed(request);

    // Overlap Neo4j + Investigation so a cold Investigation read cannot serialize behind network
    // and push Claude past the AppSail/Gateway ~20s envelope.
    long t0 = System.currentTimeMillis();
    CompletableFuture<EntityNetwork> networkFuture =
        CompletableFuture.supplyAsync(
            () -> entityNetworkService.getNetwork(seed.entityId(), QUERY_GRAPH_DEPTH));
    CompletableFuture<Optional<InvestigationRiskProfileSnapshot>> riskFuture =
        seed.kind() == QuerySeedKind.ACCUSED
            ? CompletableFuture.supplyAsync(
                () -> investigationRiskProfileClient.findRiskProfile(seed.entityId()))
            : CompletableFuture.completedFuture(Optional.empty());

    EntityNetwork network = networkFuture.join();
    log.info(
        "ask phase=network seed={} depth={} nodes={} tookMs={}",
        seed.entityId(),
        network.depth(),
        network.nodes().size(),
        System.currentTimeMillis() - t0);

    Optional<InvestigationRiskProfileSnapshot> risk = riskFuture.join();
    if (seed.kind() == QuerySeedKind.ACCUSED) {
      log.info(
          "ask phase=investigation seed={} present={} tookMs={}",
          seed.entityId(),
          risk.isPresent(),
          System.currentTimeMillis() - t0);
    }

    String context =
        QueryContextAssembler.assemble(seed.kind().name(), seed.entityId(), risk, network);

    t0 = System.currentTimeMillis();
    ClaudeAnswer model =
        claudeQueryBridge.complete(context, seed.entityId(), priorTurnsBlock, AskTask.GRAPH_BRIEFING);
    log.info(
        "ask phase=claude seed={} confidence={} historyChars={} tookMs={}",
        seed.entityId(),
        model.confidenceScore(),
        priorTurnsBlock == null || priorTurnsBlock.isBlank() ? 0 : priorTurnsBlock.length(),
        System.currentTimeMillis() - t0);

    List<String> relatedFirs =
        withoutSeed(
            model.relatedFirs().isEmpty()
                ? QueryContextAssembler.firIdsFromNetwork(network)
                : model.relatedFirs(),
            seed.entityId());
    List<RelatedEntityResource> relatedEntities =
        dedupeEntities(
            model.relatedEntities().isEmpty()
                ? entitiesFromNetwork(network, seed.entityId())
                : mapEntities(model.relatedEntities()),
            seed.entityId(),
            relatedFirs);
    // Evidence = citations not already shown under Related FIRs / Related entities (seed kept).
    List<String> evidence =
        evidenceMinusRelated(
            mergeEvidence(
                model.evidenceSources(),
                QueryContextAssembler.defaultEvidence(seed.entityId(), risk, network)),
            seed.entityId(),
            relatedFirs,
            relatedEntities);

    long latencyMs = System.currentTimeMillis() - started;
    String threadId =
        conversationId == null || conversationId.isBlank()
            ? UUID.randomUUID().toString()
            : conversationId.trim();
    log.info("ask done seed={} conversationId={} latencyMs={}", seed.entityId(), threadId, latencyMs);
    return new QueryResource(
        UUID.randomUUID().toString(),
        threadId,
        model.answer(),
        evidence,
        relatedFirs,
        relatedEntities,
        model.confidenceScore(),
        model.reasoningSummary(),
        latencyMs);
  }

  /**
   * mvp2/12 Step F — similar-cases ask: PgVector ANN from a probe FIR → Claude over those hits only
   * (same conversational envelope as {@link #ask}).
   */
  public QueryResource askSimilar(
      String probeFirId, String conversationId, String priorTurnsBlock, String officerQuestion) {
    long started = System.currentTimeMillis();
    String firId = EntityIdFormat.requireValid(probeFirId);
    requirePrefix(firId, "FIR-", "firId");

    long t0 = System.currentTimeMillis();
    SimilarCasesResult similar = similarCasesService.findSimilar(firId, null);
    List<SimilarCase> hits = similar.similarCases();
    log.info(
        "ask phase=similar probeFir={} hits={} tookMs={}",
        firId,
        hits.size(),
        System.currentTimeMillis() - t0);

    String context = QueryContextAssembler.assembleSimilar(firId, hits, officerQuestion);

    t0 = System.currentTimeMillis();
    ClaudeAnswer model =
        claudeQueryBridge.complete(
            context, firId, priorTurnsBlock, AskTask.SIMILAR_CASES);
    log.info(
        "ask phase=claude mode=similar seed={} confidence={} historyChars={} tookMs={}",
        firId,
        model.confidenceScore(),
        priorTurnsBlock == null || priorTurnsBlock.isBlank() ? 0 : priorTurnsBlock.length(),
        System.currentTimeMillis() - t0);

    Set<String> annIds = new LinkedHashSet<>();
    for (SimilarCase hit : hits) {
      if (hit.firId() != null && !hit.firId().isBlank()) {
        annIds.add(hit.firId());
      }
    }
    List<String> annList = List.copyOf(annIds);
    List<String> relatedFirs =
        withoutSeed(
            filterToAllowed(
                model.relatedFirs().isEmpty() ? annList : model.relatedFirs(), annIds),
            firId);
    if (relatedFirs.isEmpty()) {
      relatedFirs = withoutSeed(annList, firId);
    }
    List<RelatedEntityResource> relatedEntities =
        dedupeEntities(
            model.relatedEntities().isEmpty()
                ? List.of()
                : mapEntities(model.relatedEntities()),
            firId,
            relatedFirs);
    List<String> evidence =
        evidenceMinusRelated(
            mergeEvidence(model.evidenceSources(), mergeEvidence(List.of(firId), annList)),
            firId,
            relatedFirs,
            relatedEntities);

    long latencyMs = System.currentTimeMillis() - started;
    String threadId =
        conversationId == null || conversationId.isBlank()
            ? UUID.randomUUID().toString()
            : conversationId.trim();
    log.info(
        "ask done mode=similar seed={} conversationId={} latencyMs={}",
        firId,
        threadId,
        latencyMs);
    return new QueryResource(
        UUID.randomUUID().toString(),
        threadId,
        model.answer(),
        evidence,
        relatedFirs,
        relatedEntities,
        model.confidenceScore(),
        model.reasoningSummary(),
        latencyMs);
  }

  /** Keep model-cited FIR ids that appear in the ANN hit set (no invented neighbors). */
  private static List<String> filterToAllowed(List<String> ids, Set<String> allowed) {
    List<String> out = new ArrayList<>();
    for (String id : ids) {
      if (id != null && !id.isBlank() && allowed.contains(id)) {
        out.add(id);
      }
    }
    return List.copyOf(out);
  }

  static QuerySeed resolveSeed(QueryRequest request) {
    if (request == null) {
      throw new ValidationException("request body is required");
    }
    boolean hasAccused = request.accusedId() != null && !request.accusedId().isBlank();
    boolean hasFir = request.firId() != null && !request.firId().isBlank();
    if (hasAccused == hasFir) {
      throw new ValidationException("Exactly one of accusedId or firId is required");
    }
    if (hasAccused) {
      String id = EntityIdFormat.requireValid(request.accusedId());
      requirePrefix(id, "ACC-", "accusedId");
      return new QuerySeed(QuerySeedKind.ACCUSED, id);
    }
    String id = EntityIdFormat.requireValid(request.firId());
    requirePrefix(id, "FIR-", "firId");
    return new QuerySeed(QuerySeedKind.FIR, id);
  }

  private static void requirePrefix(String id, String prefix, String field) {
    if (!id.regionMatches(true, 0, prefix, 0, prefix.length())) {
      throw new ValidationException(
          field + " must start with " + prefix + " (got \"" + id + "\")", id);
    }
  }

  private static List<String> mergeEvidence(List<String> fromModel, List<String> fallback) {
    Set<String> ids = new LinkedHashSet<>();
    if (fromModel != null) {
      ids.addAll(fromModel);
    }
    if (ids.isEmpty() && fallback != null) {
      ids.addAll(fallback);
    }
    return List.copyOf(ids);
  }

  /** Drop the seed from "related" lists — it is the query subject, not a related hit. */
  private static List<String> withoutSeed(List<String> ids, String seedId) {
    List<String> out = new ArrayList<>();
    for (String id : ids) {
      if (id != null && !id.isBlank() && !id.equals(seedId)) {
        out.add(id);
      }
    }
    return List.copyOf(out);
  }

  private static List<RelatedEntityResource> dedupeEntities(
      List<RelatedEntityResource> entities, String seedId, List<String> relatedFirs) {
    Set<String> firIds = new LinkedHashSet<>(relatedFirs);
    List<RelatedEntityResource> out = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (RelatedEntityResource e : entities) {
      if (e == null || e.id() == null || e.id().isBlank()) {
        continue;
      }
      if (e.id().equals(seedId) || firIds.contains(e.id()) || !seen.add(e.id())) {
        continue;
      }
      out.add(e);
    }
    return List.copyOf(out);
  }

  /**
   * Evidence chips exclude ids already listed as Related FIRs / Related entities. Always keep the
   * seed so the citation strip is never empty when those sections cover the graph.
   */
  private static List<String> evidenceMinusRelated(
      List<String> evidence,
      String seedId,
      List<String> relatedFirs,
      List<RelatedEntityResource> relatedEntities) {
    Set<String> covered = new LinkedHashSet<>(relatedFirs);
    for (RelatedEntityResource e : relatedEntities) {
      covered.add(e.id());
    }
    Set<String> out = new LinkedHashSet<>();
    if (seedId != null && !seedId.isBlank()) {
      out.add(seedId);
    }
    for (String id : evidence) {
      if (id != null && !id.isBlank() && !covered.contains(id)) {
        out.add(id);
      }
    }
    return List.copyOf(out);
  }

  private static List<RelatedEntityResource> mapEntities(List<RelatedEntity> entities) {
    List<RelatedEntityResource> out = new ArrayList<>(entities.size());
    for (RelatedEntity e : entities) {
      out.add(new RelatedEntityResource(e.id(), e.type(), humanizeLabel(e.label())));
    }
    return List.copyOf(out);
  }

  private static List<RelatedEntityResource> entitiesFromNetwork(
      EntityNetwork network, String seedId) {
    List<RelatedEntityResource> out = new ArrayList<>();
    for (NetworkNode node : network.nodes()) {
      if (node.id().equals(seedId)) {
        continue;
      }
      out.add(new RelatedEntityResource(node.id(), node.type(), humanizeLabel(node.label())));
    }
    return List.copyOf(out);
  }

  /** Officer-facing labels: MARKET_AREA → Market area; InvestigationOfficer left as type. */
  static String humanizeLabel(String raw) {
    if (raw == null || raw.isBlank()) {
      return raw;
    }
    StringBuilder sb = new StringBuilder(raw.length());
    for (String token : raw.split("(?<=\\s)|(?=\\s)")) {
      if (token.isBlank()) {
        sb.append(token);
        continue;
      }
      if (token.indexOf('_') >= 0 || token.matches("[A-Z]{2,}[A-Z0-9_]*")) {
        String[] parts = token.split("_");
        for (int i = 0; i < parts.length; i++) {
          if (i > 0) {
            sb.append(' ');
          }
          String w = parts[i];
          if (w.isEmpty()) {
            continue;
          }
          sb.append(Character.toUpperCase(w.charAt(0)));
          if (w.length() > 1) {
            sb.append(w.substring(1).toLowerCase());
          }
        }
      } else {
        sb.append(token);
      }
    }
    return sb.toString();
  }
}

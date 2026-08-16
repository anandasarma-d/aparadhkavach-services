package dev.aparadhkavach.orchestration.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.query.client.InvestigationRiskProfileClient;
import dev.aparadhkavach.orchestration.query.client.InvestigationRiskProfileSnapshot;
import dev.aparadhkavach.orchestration.query.service.ClaudeQueryBridge.ClaudeAnswer;
import dev.aparadhkavach.orchestration.dto.QueryRequest;
import dev.aparadhkavach.orchestration.dto.QueryResource;
import dev.aparadhkavach.orchestration.graph.config.GraphProperties;
import dev.aparadhkavach.orchestration.graph.model.EntityNetwork;
import dev.aparadhkavach.orchestration.graph.model.NetworkEdge;
import dev.aparadhkavach.orchestration.graph.model.NetworkNode;
import dev.aparadhkavach.orchestration.graph.repository.EntityNetworkRepository;
import dev.aparadhkavach.orchestration.graph.service.EntityNetworkService;
import dev.aparadhkavach.orchestration.search.config.VectorProperties;
import dev.aparadhkavach.orchestration.search.model.SimilarCase;
import dev.aparadhkavach.orchestration.search.model.SimilarCasesResult;
import dev.aparadhkavach.orchestration.search.service.SimilarCasesService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Mockito-free (JDK 25 agent attach is flaky in this environment). Stubs record depth / calls.
 */
class QueryServiceTest {

  @Test
  void resolveSeed_rejectsBothOrNeither() {
    assertThatThrownBy(() -> QueryService.resolveSeed(new QueryRequest(null, null)))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> QueryService.resolveSeed(new QueryRequest("ACC-1", "FIR-1")))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> QueryService.resolveSeed(new QueryRequest("  ", "  ")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void resolveSeed_rejectsCrossTypeIds() {
    assertThatThrownBy(() -> QueryService.resolveSeed(new QueryRequest("FIR-002683", null)))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("accusedId must start with ACC-");
    assertThatThrownBy(() -> QueryService.resolveSeed(new QueryRequest(null, "ACC-00040")))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("firId must start with FIR-");
  }

  @Test
  void resolveSeed_acceptsMatchingPrefixes() {
    assertThat(QueryService.resolveSeed(new QueryRequest("ACC-00040", null)).entityId())
        .isEqualTo("ACC-00040");
    assertThat(QueryService.resolveSeed(new QueryRequest(null, "FIR-003276")).entityId())
        .isEqualTo("FIR-003276");
  }

  @Test
  void ask_hardCapsGraphDepthToOne() {
    AtomicInteger depthSeen = new AtomicInteger(-1);
    EntityNetworkService networkService = recordingNetworkService(depthSeen, sampleNetwork());
    InvestigationRiskProfileClient investigation =
        new InvestigationRiskProfileClient(RestClient.create()) {
          @Override
          public Optional<InvestigationRiskProfileSnapshot> findRiskProfile(String accusedId) {
            return Optional.of(
                new InvestigationRiskProfileSnapshot(
                    "ACC-00040", "Praneel Andra", "d1", 2, new BigDecimal("0.71")));
          }
        };
    ClaudeQueryBridge bridge =
        ClaudeQueryBridge.forTests(new ObjectMapper(), "local-dev-placeholder-not-a-real-key");
    QueryService service =
        new QueryService(networkService, investigation, bridge, stubSimilarCasesService());

    QueryResource result = service.ask(new QueryRequest("ACC-00040", null));

    assertThat(depthSeen.get()).isEqualTo(QueryService.QUERY_GRAPH_DEPTH);
    assertThat(result.conversationId()).isNotBlank();
    assertThat(result.conversationId()).isNotEqualTo("mvp2-qa-sentinel");
    assertThat(result.answer()).contains("Answer generation is not configured");
    assertThat(result.evidenceSources()).isNotEmpty();
  }

  @Test
  void ask_firPath_skipsInvestigationRiskLookup() {
    AtomicInteger depthSeen = new AtomicInteger(-1);
    AtomicReference<String> investigationCalled = new AtomicReference<>(null);
    EntityNetworkService networkService = recordingNetworkService(depthSeen, sampleNetwork());
    InvestigationRiskProfileClient investigation =
        new InvestigationRiskProfileClient(RestClient.create()) {
          @Override
          public Optional<InvestigationRiskProfileSnapshot> findRiskProfile(String accusedId) {
            investigationCalled.set(accusedId);
            return Optional.empty();
          }
        };
    ClaudeQueryBridge bridge =
        ClaudeQueryBridge.forTests(new ObjectMapper(), "local-dev-placeholder-not-a-real-key");
    QueryService service =
        new QueryService(networkService, investigation, bridge, stubSimilarCasesService());

    service.ask(new QueryRequest(null, "FIR-003276"));

    assertThat(depthSeen.get()).isEqualTo(1);
    assertThat(investigationCalled.get()).isNull();
  }

  @Test
  void claudeBridge_parse_allowsEmptyEvidence() {
    ClaudeQueryBridge live = ClaudeQueryBridge.forTests(new ObjectMapper(), "sk-ant-test-key");
    ClaudeAnswer answer =
        live.parse(
            """
            {"answer":"x","evidenceSources":[],"relatedFirs":[],"relatedEntities":[],"confidenceScore":0.5,"reasoningSummary":"y"}
            """);
    assertThat(answer.evidenceSources()).isEmpty();
    assertThat(answer.answer()).isEqualTo("x");
  }

  @Test
  void claudeBridge_parse_happy() {
    ClaudeQueryBridge live = ClaudeQueryBridge.forTests(new ObjectMapper(), "sk-ant-test-key");
    ClaudeAnswer answer =
        live.parse(
            """
            {"answer":"Linked via FIR-1","evidenceSources":["ACC-00040","FIR-1"],"relatedFirs":["FIR-1"],"relatedEntities":[{"id":"ACC-2","type":"Accused","label":"X"}],"confidenceScore":0.8,"reasoningSummary":"From graph"}
            """);
    assertThat(answer.answer()).contains("Linked");
    assertThat(answer.evidenceSources()).containsExactly("ACC-00040", "FIR-1");
    assertThat(answer.relatedEntities()).hasSize(1);
  }

  @Test
  void askSimilar_usesAnnHitsAsRelatedFirs() {
    AtomicInteger depthSeen = new AtomicInteger(-1);
    EntityNetworkService networkService = recordingNetworkService(depthSeen, sampleNetwork());
    InvestigationRiskProfileClient investigation =
        new InvestigationRiskProfileClient(RestClient.create()) {
          @Override
          public Optional<InvestigationRiskProfileSnapshot> findRiskProfile(String accusedId) {
            return Optional.empty();
          }
        };
    ClaudeQueryBridge bridge =
        ClaudeQueryBridge.forTests(new ObjectMapper(), "local-dev-placeholder-not-a-real-key");
    QueryService service =
        new QueryService(networkService, investigation, bridge, stubSimilarCasesService());

    QueryResource result =
        service.askSimilar("FIR-002683", "conv-1", null, "find similar cases");

    assertThat(depthSeen.get()).isEqualTo(-1); // Neo4j not used on similar path
    assertThat(result.relatedFirs()).contains("FIR-999001", "FIR-999002");
    assertThat(result.evidenceSources()).contains("FIR-002683");
    assertThat(result.answer()).contains("Answer generation is not configured");
  }

  private static SimilarCasesService stubSimilarCasesService() {
    return new SimilarCasesService(null, new VectorProperties(), null) {
      @Override
      public SimilarCasesResult findSimilar(String rawFirId, Integer requestedLimit) {
        return new SimilarCasesResult(
            rawFirId,
            5,
            List.of(
                new SimilarCase("FIR-999001", 0.94, "Mysuru", "Burglary", null, "UI"),
                new SimilarCase("FIR-999002", 0.91, "Kodagu", "Burglary", null, "UI")));
      }
    };
  }

  private static EntityNetworkService recordingNetworkService(
      AtomicInteger depthSeen, EntityNetwork network) {
    GraphProperties props = new GraphProperties();
    props.setNetworkDefaultDepth(1);
    props.setNetworkMaxDepth(2);
    props.setNetworkMaxNodes(50);
    // Repository unused — getNetwork is overridden for the test.
    return new EntityNetworkService((EntityNetworkRepository) null, props) {
      @Override
      public EntityNetwork getNetwork(String rawEntityId, Integer requestedDepth) {
        depthSeen.set(requestedDepth == null ? -1 : requestedDepth);
        return network;
      }
    };
  }

  private static EntityNetwork sampleNetwork() {
    return new EntityNetwork(
        "ACC-00040",
        "Praneel Andra",
        1,
        List.of(
            new NetworkNode("ACC-00040", "Accused", "Praneel Andra"),
            new NetworkNode("FIR-003276", "FIR", "Cybercrime")),
        List.of(new NetworkEdge("ACC-00040", "FIR-003276", "NAMED_IN")),
        false);
  }
}

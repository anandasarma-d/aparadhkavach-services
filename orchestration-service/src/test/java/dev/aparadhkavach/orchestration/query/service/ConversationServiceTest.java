package dev.aparadhkavach.orchestration.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.dto.QueryRequest;
import dev.aparadhkavach.orchestration.dto.QueryResource;
import dev.aparadhkavach.orchestration.graph.config.GraphProperties;
import dev.aparadhkavach.orchestration.graph.model.EntityNetwork;
import dev.aparadhkavach.orchestration.graph.model.NetworkNode;
import dev.aparadhkavach.orchestration.graph.repository.EntityNetworkRepository;
import dev.aparadhkavach.orchestration.graph.service.EntityNetworkService;
import dev.aparadhkavach.orchestration.query.client.InvestigationRiskProfileClient;
import dev.aparadhkavach.orchestration.query.client.InvestigationRiskProfileSnapshot;
import dev.aparadhkavach.orchestration.query.conversation.ConversationMessageRole;
import dev.aparadhkavach.orchestration.query.conversation.InMemoryConversationStore;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ConversationServiceTest {

  @Test
  void ask_createsConversationAndAppendsTurns() {
    ConversationService conversations = newConversationService();

    QueryResource first = conversations.ask(null, new QueryRequest("ACC-00040", null));
    assertThat(first.conversationId()).isNotBlank();

    var thread = conversations.get(first.conversationId());
    assertThat(thread.messages()).hasSize(2);
    assertThat(thread.messages().get(0).role()).isEqualTo(ConversationMessageRole.USER.name());
    assertThat(thread.messages().get(1).role()).isEqualTo(ConversationMessageRole.ASSISTANT.name());
    assertThat(thread.messages().get(1).queryId()).isEqualTo(first.queryId());

    QueryResource second =
        conversations.ask(first.conversationId(), new QueryRequest(null, "FIR-003276"));
    assertThat(second.conversationId()).isEqualTo(first.conversationId());
    assertThat(conversations.get(first.conversationId()).messages()).hasSize(4);
  }

  @Test
  void ask_followUp_resolvesCitedFir() {
    ConversationService conversations = newConversationService();
    QueryResource first = conversations.ask(null, new QueryRequest("ACC-00040", null));

    QueryResource follow =
        conversations.ask(
            first.conversationId(),
            new QueryRequest(null, null, first.conversationId(), "Tell me about FIR-003276"));

    assertThat(follow.conversationId()).isEqualTo(first.conversationId());
    assertThat(conversations.get(first.conversationId()).messages()).hasSize(4);
    assertThat(conversations.get(first.conversationId()).messages().get(2).text())
        .contains("FIR-003276");
  }

  @Test
  void ask_followUpWithoutConversation_rejected() {
    ConversationService conversations = newConversationService();
    assertThatThrownBy(
            () ->
                conversations.ask(
                    null, new QueryRequest(null, null, null, "what about the co-accused?")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void ask_followUp_storeMiss_hydratesFromContext() {
    ConversationService conversations = newConversationService();
    var ctx =
        new dev.aparadhkavach.orchestration.dto.FollowUpContext(
            null,
            "FIR-002683",
            List.of("FIR-002683"),
            List.of(),
            List.of(
                new dev.aparadhkavach.orchestration.dto.RelatedEntityResource(
                    "ACC-03047", "Accused", "Ucchal Lal")));

    QueryResource follow =
        conversations.ask(
            "missing-ephemeral-id",
            new QueryRequest(
                null, null, "missing-ephemeral-id", "what about co-accused?", ctx));

    assertThat(follow.conversationId()).isEqualTo("missing-ephemeral-id");
    assertThat(conversations.get("missing-ephemeral-id").messages().get(1).text())
        .contains("co-accused");
    // Resolved seed should be the co-accused ACC from context (USER turn after hydrate).
    assertThat(conversations.get("missing-ephemeral-id").messages().get(1).accusedId())
        .isEqualTo("ACC-03047");
  }

  @Test
  void get_unknownConversation_throws() {
    ConversationService conversations = newConversationService();
    assertThatThrownBy(() -> conversations.get("missing-id"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private static ConversationService newConversationService() {
    GraphProperties props = new GraphProperties();
    props.setNetworkDefaultDepth(1);
    props.setNetworkMaxDepth(2);
    props.setNetworkMaxNodes(50);
    EntityNetworkService networkService =
        new EntityNetworkService((EntityNetworkRepository) null, props) {
          @Override
          public EntityNetwork getNetwork(String rawEntityId, Integer requestedDepth) {
            if (rawEntityId.toUpperCase().startsWith("ACC-")) {
              return new EntityNetwork(
                  rawEntityId,
                  rawEntityId,
                  1,
                  List.of(
                      new NetworkNode(rawEntityId, "Accused", rawEntityId),
                      new NetworkNode("FIR-003276", "FIR", "Cybercrime"),
                      new NetworkNode("ACC-00439", "Accused", "Prakash Shetty")),
                  List.of(),
                  false);
            }
            return new EntityNetwork(
                rawEntityId,
                rawEntityId,
                1,
                List.of(new NetworkNode(rawEntityId, "FIR", rawEntityId)),
                List.of(),
                false);
          }
        };
    InvestigationRiskProfileClient investigation =
        new InvestigationRiskProfileClient(RestClient.create()) {
          @Override
          public Optional<InvestigationRiskProfileSnapshot> findRiskProfile(String accusedId) {
            return Optional.empty();
          }
        };
    ClaudeQueryBridge bridge =
        ClaudeQueryBridge.forTests(new ObjectMapper(), "local-dev-placeholder-not-a-real-key");
    QueryService queryService = new QueryService(networkService, investigation, bridge);
    return new ConversationService(
        new InMemoryConversationStore(), queryService, new FollowUpResolver());
  }
}

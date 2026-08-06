package dev.aparadhkavach.orchestration.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.query.conversation.Conversation;
import dev.aparadhkavach.orchestration.query.conversation.ConversationMessage;
import dev.aparadhkavach.orchestration.query.conversation.ConversationMessageRole;
import dev.aparadhkavach.orchestration.query.conversation.RelatedEntityRef;
import dev.aparadhkavach.orchestration.query.model.QuerySeedKind;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FollowUpResolverTest {

  private final FollowUpResolver resolver = new FollowUpResolver();

  @Test
  void resolvesExplicitCitedId() {
    Conversation conversation = seededConversation();
    var seed = resolver.resolve(conversation, "Please summarise FIR-000971 next");
    assertThat(seed.kind()).isEqualTo(QuerySeedKind.FIR);
    assertThat(seed.entityId()).isEqualTo("FIR-000971");
  }

  @Test
  void rejectsExplicitIdNotInCitations() {
    Conversation conversation = seededConversation();
    assertThatThrownBy(() -> resolver.resolve(conversation, "tell me about FIR-003276"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("not in this thread");
  }

  @Test
  void resolvesCoAccusedPhrase() {
    Conversation conversation = seededConversation();
    var seed = resolver.resolve(conversation, "What about those co-accused?");
    assertThat(seed.kind()).isEqualTo(QuerySeedKind.ACCUSED);
    assertThat(seed.entityId()).isEqualTo("ACC-00439");
  }

  @Test
  void resolvesLabelMatch() {
    Conversation conversation = seededConversation();
    var seed = resolver.resolve(conversation, "Tell me more about Prakash Shetty");
    assertThat(seed.entityId()).isEqualTo("ACC-00439");
  }

  @Test
  void emptyHistory_rejected() {
    Conversation empty = new Conversation("c1", Instant.now());
    assertThatThrownBy(() -> resolver.resolve(empty, "co-accused?"))
        .isInstanceOf(ValidationException.class);
  }

  private static Conversation seededConversation() {
    Conversation conversation = new Conversation("c1", Instant.now());
    conversation.append(
        new ConversationMessage(
            "m1",
            ConversationMessageRole.USER,
            Instant.now(),
            "Ask about accused ACC-00040",
            "ACC-00040",
            null,
            null,
            List.of(),
            List.of(),
            List.of()));
    conversation.append(
        new ConversationMessage(
            "m2",
            ConversationMessageRole.ASSISTANT,
            Instant.now(),
            "Briefing…",
            "ACC-00040",
            null,
            "q1",
            List.of("ACC-00040"),
            List.of("FIR-000971", "FIR-001219"),
            List.of(
                new RelatedEntityRef("ACC-00439", "Accused", "Prakash Shetty"),
                new RelatedEntityRef("ACC-02583", "Accused", "Warjas Deol"))));
    return conversation;
  }
}

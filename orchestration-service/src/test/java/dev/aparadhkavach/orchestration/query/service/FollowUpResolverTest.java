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

  @Test
  void resolvesVehiclePhraseToHostAccused() {
    Conversation conversation = conversationWithVehicle();
    // After an FIR follow-up, lastSeed is FIR — vehicle ask must still map to ACC that cited VEH.
    conversation.append(
        new ConversationMessage(
            "m3",
            ConversationMessageRole.USER,
            Instant.now(),
            "Tell me about FIR-002351",
            null,
            "FIR-002351",
            null,
            List.of(),
            List.of(),
            List.of()));
    conversation.append(
        new ConversationMessage(
            "m4",
            ConversationMessageRole.ASSISTANT,
            Instant.now(),
            "FIR briefing without vehicle…",
            null,
            "FIR-002351",
            "q2",
            List.of("FIR-002351"),
            List.of(),
            List.of()));

    var seed = resolver.resolve(conversation, "what about the vehicle used?");
    assertThat(seed.kind()).isEqualTo(QuerySeedKind.ACCUSED);
    assertThat(seed.entityId()).isEqualTo("ACC-00031");
  }

  @Test
  void resolvesVehiclePlateTypoToHostAccused() {
    Conversation conversation = conversationWithVehicle();
    var seed = resolver.resolve(conversation, "tell me about the vehicle KA-16-QO7110");
    assertThat(seed.kind()).isEqualTo(QuerySeedKind.ACCUSED);
    assertThat(seed.entityId()).isEqualTo("ACC-00031");
  }

  @Test
  void resolvesExplicitVehIdToHostAccused() {
    Conversation conversation = conversationWithVehicle();
    var seed = resolver.resolve(conversation, "Tell me about VEH-00768");
    assertThat(seed.kind()).isEqualTo(QuerySeedKind.ACCUSED);
    assertThat(seed.entityId()).isEqualTo("ACC-00031");
  }

  @Test
  void vehicleWithNoCitedVehicle_rejected() {
    Conversation conversation = seededConversation();
    assertThatThrownBy(() -> resolver.resolve(conversation, "what about the vehicle used?"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("no cited vehicle");
  }

  private static Conversation conversationWithVehicle() {
    Conversation conversation = new Conversation("c-veh", Instant.now());
    conversation.append(
        new ConversationMessage(
            "m1",
            ConversationMessageRole.USER,
            Instant.now(),
            "Ask about accused ACC-00031",
            "ACC-00031",
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
            "Owns KA-16-QQ7110…",
            "ACC-00031",
            null,
            "q1",
            List.of("ACC-00031", "VEH-00768"),
            List.of("FIR-002351"),
            List.of(
                new RelatedEntityRef("VEH-00768", "Vehicle", "KA-16-QQ7110"),
                new RelatedEntityRef("ACC-04013", "Accused", "Shivani Rai"))));
    return conversation;
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

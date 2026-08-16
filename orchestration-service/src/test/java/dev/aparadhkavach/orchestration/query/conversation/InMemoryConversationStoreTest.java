package dev.aparadhkavach.orchestration.query.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryConversationStoreTest {

  @Test
  void createAppendFindRoundTrip() {
    ConversationStore store = new InMemoryConversationStore();
    Conversation created = store.create();
    store.append(
        created.conversationId(),
        new ConversationMessage(
            "m1",
            ConversationMessageRole.USER,
            Instant.now(),
            "Ask about accused ACC-00001",
            "ACC-00001",
            null,
            null,
            List.of(),
            List.of(),
            List.of()));
    store.append(
        created.conversationId(),
        new ConversationMessage(
            "m2",
            ConversationMessageRole.ASSISTANT,
            Instant.now(),
            "Briefing…",
            "ACC-00001",
            null,
            "q1",
            List.of("ACC-00001"),
            List.of("FIR-1"),
            List.of(new RelatedEntityRef("VEH-1", "VEHICLE", "KA-01"))));

    Conversation loaded = store.require(created.conversationId());
    assertEquals(2, loaded.size());
    assertEquals(ConversationMessageRole.USER, loaded.messages().get(0).role());
    assertEquals("VEH-1", loaded.messages().get(1).relatedEntities().get(0).id());
    assertTrue(store.find("missing").isEmpty());
  }
}

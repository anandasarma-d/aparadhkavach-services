package dev.aparadhkavach.orchestration.query.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.aparadhkavach.orchestration.query.conversation.ConversationMessage;
import dev.aparadhkavach.orchestration.query.conversation.ConversationMessageRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationHistoryPackerTest {

  @Test
  void empty_returnsBlank() {
    assertThat(ConversationHistoryPacker.pack(List.of(), 6, 2500, 400)).isEmpty();
    assertThat(ConversationHistoryPacker.pack(null, 6, 2500, 400)).isEmpty();
  }

  @Test
  void packsUserAndAssistant_skipsHydratePlaceholder() {
    List<ConversationMessage> messages =
        List.of(
            msg(ConversationMessageRole.ASSISTANT, "(prior answer citations)", null, "FIR-002683"),
            msg(ConversationMessageRole.USER, "Ask about accused ACC-00040", "ACC-00040", null),
            msg(
                ConversationMessageRole.ASSISTANT,
                "Briefing on ACC-00040 linked to FIR-000971.",
                "ACC-00040",
                null),
            msg(ConversationMessageRole.USER, "tell me about FIR-000971", null, "FIR-000971"));

    String packed = ConversationHistoryPacker.pack(messages, 6, 2500, 400);
    assertThat(packed).contains("PRIOR_TURNS");
    assertThat(packed).contains("USER [ACC-00040]");
    assertThat(packed).contains("ASSISTANT [ACC-00040]");
    assertThat(packed).contains("FIR-000971");
    assertThat(packed).doesNotContain("(prior answer citations)");
    assertThat(packed).contains("do NOT treat as evidence");
    assertThat(packed).doesNotContain("Officer");
    assertThat(packed).doesNotContain("Assist");
  }

  @Test
  void respectsMaxTurns() {
    List<ConversationMessage> messages =
        List.of(
            msg(ConversationMessageRole.USER, "first", "ACC-1", null),
            msg(ConversationMessageRole.ASSISTANT, "a1", "ACC-1", null),
            msg(ConversationMessageRole.USER, "second", "ACC-2", null),
            msg(ConversationMessageRole.ASSISTANT, "a2", "ACC-2", null),
            msg(ConversationMessageRole.USER, "third", "ACC-3", null),
            msg(ConversationMessageRole.ASSISTANT, "a3", "ACC-3", null));

    String packed = ConversationHistoryPacker.pack(messages, 2, 2500, 400);
    assertThat(packed).contains("third");
    assertThat(packed).contains("a3");
    assertThat(packed).doesNotContain("first");
    assertThat(packed).doesNotContain("second");
  }

  private static ConversationMessage msg(
      ConversationMessageRole role, String text, String accusedId, String firId) {
    return new ConversationMessage(
        "m",
        role,
        Instant.now(),
        text,
        accusedId,
        firId,
        null,
        List.of(),
        List.of(),
        List.of());
  }
}

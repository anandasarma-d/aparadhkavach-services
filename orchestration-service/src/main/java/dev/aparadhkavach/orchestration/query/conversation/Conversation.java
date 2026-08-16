package dev.aparadhkavach.orchestration.query.conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Conversation thread domain object (loaded from {@link ConversationStore} — mvp2/12 A/G). */
public final class Conversation {

  private final String conversationId;
  private final Instant createdAt;
  private final List<ConversationMessage> messages = new ArrayList<>();

  public Conversation(String conversationId, Instant createdAt) {
    this.conversationId = conversationId;
    this.createdAt = createdAt;
  }

  public String conversationId() {
    return conversationId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public synchronized List<ConversationMessage> messages() {
    return Collections.unmodifiableList(List.copyOf(messages));
  }

  public synchronized void append(ConversationMessage message) {
    messages.add(message);
  }

  public synchronized int size() {
    return messages.size();
  }
}

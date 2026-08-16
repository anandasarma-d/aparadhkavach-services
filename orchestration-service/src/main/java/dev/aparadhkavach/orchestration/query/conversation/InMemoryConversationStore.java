package dev.aparadhkavach.orchestration.query.conversation;

import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Process-local conversation store (mvp2/12 Step A). Used when {@code
 * aparadhkavach.query.conversation-store=memory} (tests / local without Postgres tables). Lane B
 * AppSail defaults to JDBC (Step G).
 */
@Component
@ConditionalOnProperty(
    prefix = "aparadhkavach.query",
    name = "conversation-store",
    havingValue = "memory")
public class InMemoryConversationStore implements ConversationStore {

  private final ConcurrentHashMap<String, Conversation> byId = new ConcurrentHashMap<>();

  @Override
  public Conversation create() {
    return create(UUID.randomUUID().toString());
  }

  @Override
  public Conversation create(String conversationId) {
    String id =
        conversationId == null || conversationId.isBlank()
            ? UUID.randomUUID().toString()
            : conversationId.trim();
    Conversation conversation = new Conversation(id, Instant.now());
    byId.put(id, conversation);
    return conversation;
  }

  @Override
  public Optional<Conversation> find(String conversationId) {
    if (conversationId == null || conversationId.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byId.get(conversationId.trim()));
  }

  @Override
  public Conversation require(String conversationId) {
    return find(conversationId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "No conversation for conversationId=" + conversationId));
  }

  @Override
  public void append(String conversationId, ConversationMessage message) {
    require(conversationId).append(message);
  }
}

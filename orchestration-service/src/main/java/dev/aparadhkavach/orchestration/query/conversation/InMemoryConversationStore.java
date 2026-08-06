package dev.aparadhkavach.orchestration.query.conversation;

import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Process-local conversation store (mvp2/12 Step A). Survives only for the AppSail instance
 * lifetime — fine for Lane B demos; durable store is out of scope for this slice.
 */
@Component
public class InMemoryConversationStore {

  private final ConcurrentHashMap<String, Conversation> byId = new ConcurrentHashMap<>();

  public Conversation create() {
    return create(UUID.randomUUID().toString());
  }

  /** Create (or replace) a conversation under a known id — used when hydrating after a store miss. */
  public Conversation create(String conversationId) {
    String id =
        conversationId == null || conversationId.isBlank()
            ? UUID.randomUUID().toString()
            : conversationId.trim();
    Conversation conversation = new Conversation(id, Instant.now());
    byId.put(id, conversation);
    return conversation;
  }

  public Optional<Conversation> find(String conversationId) {
    if (conversationId == null || conversationId.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byId.get(conversationId.trim()));
  }

  public Conversation require(String conversationId) {
    return find(conversationId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "No conversation for conversationId=" + conversationId));
  }
}

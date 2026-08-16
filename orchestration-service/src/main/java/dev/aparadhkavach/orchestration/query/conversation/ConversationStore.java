package dev.aparadhkavach.orchestration.query.conversation;

import java.util.Optional;

/**
 * Conversation thread persistence (mvp2/12 Step A / G). Process-local {@link
 * InMemoryConversationStore} for tests / local; {@link JdbcConversationStore} for Lane B AppSail
 * (Supabase Postgres — same JDBC as {@code fir_embeddings}).
 */
public interface ConversationStore {

  Conversation create();

  /** Create (or replace) a conversation under a known id — used when hydrating after a store miss. */
  Conversation create(String conversationId);

  Optional<Conversation> find(String conversationId);

  Conversation require(String conversationId);

  /** Persist one turn. Implementations must make the message visible to a subsequent {@link #find}. */
  void append(String conversationId, ConversationMessage message);
}

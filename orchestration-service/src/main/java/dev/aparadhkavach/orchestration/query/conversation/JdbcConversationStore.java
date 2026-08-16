package dev.aparadhkavach.orchestration.query.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Durable conversation store on Supabase Postgres (mvp2/12 Step G). Same JDBC pool as {@code
 * fir_embeddings}. Tables: {@code qa_conversations} / {@code qa_messages} — see recreate {@code
 * schema.sql}. TTL: rows older than {@code conversation-ttl-days} are treated as miss.
 */
@Component
@ConditionalOnProperty(
    prefix = "aparadhkavach.query",
    name = "conversation-store",
    havingValue = "jdbc",
    matchIfMissing = true)
public class JdbcConversationStore implements ConversationStore {

  private static final Logger log = LoggerFactory.getLogger(JdbcConversationStore.class);

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private static final TypeReference<List<RelatedEntityRef>> ENTITY_LIST = new TypeReference<>() {};

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final int ttlDays;

  public JdbcConversationStore(
      NamedParameterJdbcTemplate jdbc,
      ObjectMapper objectMapper,
      @Value("${aparadhkavach.query.conversation-ttl-days:7}") int ttlDays) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.ttlDays = Math.max(1, ttlDays);
  }

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
    Instant now = Instant.now();
    Map<String, Object> params = new HashMap<>();
    params.put("id", id);
    params.put("createdAt", Timestamp.from(now));
    jdbc.update(
        """
        INSERT INTO qa_conversations (conversation_id, created_at)
        VALUES (:id, :createdAt)
        ON CONFLICT (conversation_id) DO UPDATE SET created_at = EXCLUDED.created_at
        """,
        params);
    jdbc.update("DELETE FROM qa_messages WHERE conversation_id = :id", Map.of("id", id));
    return new Conversation(id, now);
  }

  @Override
  public Optional<Conversation> find(String conversationId) {
    if (conversationId == null || conversationId.isBlank()) {
      return Optional.empty();
    }
    String id = conversationId.trim();
    Instant cutoff = Instant.now().minus(ttlDays, ChronoUnit.DAYS);
    Instant createdAt;
    try {
      Map<String, Object> params = new HashMap<>();
      params.put("id", id);
      params.put("cutoff", Timestamp.from(cutoff));
      createdAt =
          jdbc.queryForObject(
              """
              SELECT created_at FROM qa_conversations
              WHERE conversation_id = :id AND created_at >= :cutoff
              """,
              params,
              (rs, rowNum) -> rs.getTimestamp("created_at").toInstant());
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
    if (createdAt == null) {
      return Optional.empty();
    }
    Conversation conversation = new Conversation(id, createdAt);
    List<ConversationMessage> messages =
        jdbc.query(
            """
            SELECT message_id, role, created_at, text, accused_id, fir_id, query_id,
                   evidence_sources::text AS evidence_sources,
                   related_firs::text AS related_firs,
                   related_entities::text AS related_entities
            FROM qa_messages
            WHERE conversation_id = :id
            ORDER BY seq ASC
            """,
            Map.of("id", id),
            (rs, rowNum) ->
                new ConversationMessage(
                    rs.getString("message_id"),
                    ConversationMessageRole.valueOf(rs.getString("role")),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getString("text"),
                    blankToNull(rs.getString("accused_id")),
                    blankToNull(rs.getString("fir_id")),
                    blankToNull(rs.getString("query_id")),
                    readStringList(rs.getString("evidence_sources")),
                    readStringList(rs.getString("related_firs")),
                    readEntityList(rs.getString("related_entities"))));
    for (ConversationMessage message : messages) {
      conversation.append(message);
    }
    return Optional.of(conversation);
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
    if (message == null) {
      return;
    }
    String id = conversationId == null ? "" : conversationId.trim();
    if (id.isBlank()) {
      throw new ResourceNotFoundException("No conversation for blank conversationId");
    }
    Integer exists =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM qa_conversations WHERE conversation_id = :id",
            Map.of("id", id),
            Integer.class);
    if (exists == null || exists == 0) {
      Map<String, Object> createParams = new HashMap<>();
      createParams.put("id", id);
      createParams.put("createdAt", Timestamp.from(Instant.now()));
      jdbc.update(
          """
          INSERT INTO qa_conversations (conversation_id, created_at)
          VALUES (:id, :createdAt)
          ON CONFLICT (conversation_id) DO NOTHING
          """,
          createParams);
    }
    Map<String, Object> params = new HashMap<>();
    params.put("messageId", message.messageId());
    params.put("conversationId", id);
    params.put("role", message.role().name());
    params.put("createdAt", Timestamp.from(message.createdAt()));
    params.put("text", message.text() == null ? "" : message.text());
    params.put("accusedId", blankToNull(message.accusedId()));
    params.put("firId", blankToNull(message.firId()));
    params.put("queryId", blankToNull(message.queryId()));
    params.put("evidence", toJson(message.evidenceSources()));
    params.put("firs", toJson(message.relatedFirs()));
    params.put("entities", toJson(message.relatedEntities()));
    jdbc.update(
        """
        INSERT INTO qa_messages (
          message_id, conversation_id, role, created_at, text,
          accused_id, fir_id, query_id,
          evidence_sources, related_firs, related_entities)
        VALUES (
          :messageId, :conversationId, :role, :createdAt, :text,
          :accusedId, :firId, :queryId,
          CAST(:evidence AS jsonb), CAST(:firs AS jsonb), CAST(:entities AS jsonb))
        """,
        params);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? List.of() : value);
    } catch (JsonProcessingException e) {
      log.warn("conversation JSON serialize failed: {}", e.getMessage());
      return "[]";
    }
  }

  private List<String> readStringList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      List<String> list = objectMapper.readValue(json, STRING_LIST);
      return list == null ? List.of() : List.copyOf(list);
    } catch (JsonProcessingException e) {
      log.warn("conversation string-list parse failed: {}", e.getMessage());
      return List.of();
    }
  }

  private List<RelatedEntityRef> readEntityList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      List<RelatedEntityRef> list = objectMapper.readValue(json, ENTITY_LIST);
      return list == null ? List.of() : List.copyOf(list);
    } catch (JsonProcessingException e) {
      log.warn("conversation entity-list parse failed: {}", e.getMessage());
      return List.of();
    }
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}

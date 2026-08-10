package dev.aparadhkavach.orchestration.query.service;

import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.dto.ConversationCreatedResource;
import dev.aparadhkavach.orchestration.dto.ConversationMessageResource;
import dev.aparadhkavach.orchestration.dto.ConversationResource;
import dev.aparadhkavach.orchestration.dto.FollowUpContext;
import dev.aparadhkavach.orchestration.dto.QueryRequest;
import dev.aparadhkavach.orchestration.dto.QueryResource;
import dev.aparadhkavach.orchestration.dto.RelatedEntityResource;
import dev.aparadhkavach.orchestration.query.config.QueryProperties;
import dev.aparadhkavach.orchestration.query.conversation.Conversation;
import dev.aparadhkavach.orchestration.query.conversation.ConversationMessage;
import dev.aparadhkavach.orchestration.query.conversation.ConversationMessageRole;
import dev.aparadhkavach.orchestration.query.conversation.InMemoryConversationStore;
import dev.aparadhkavach.orchestration.query.conversation.RelatedEntityRef;
import dev.aparadhkavach.orchestration.query.model.QuerySeed;
import dev.aparadhkavach.orchestration.query.model.QuerySeedKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Conversation CRUD + ask-with-thread (mvp2/12 Step A/B/D/F). Follow-ups resolve to ACC-/FIR-
 * seeds from prior citations, or to a similar-cases PgVector ask (Step F), then pack a bounded
 * history window into Claude (Step D).
 */
@Service
public class ConversationService {

  private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

  private final InMemoryConversationStore store;
  private final QueryService queryService;
  private final FollowUpResolver followUpResolver;
  private final QueryProperties queryProperties;

  public ConversationService(
      InMemoryConversationStore store,
      QueryService queryService,
      FollowUpResolver followUpResolver,
      QueryProperties queryProperties) {
    this.store = store;
    this.queryService = queryService;
    this.followUpResolver = followUpResolver;
    this.queryProperties = queryProperties;
  }

  public ConversationCreatedResource create() {
    Conversation conversation = store.create();
    return new ConversationCreatedResource(conversation.conversationId(), conversation.createdAt());
  }

  public ConversationResource get(String conversationId) {
    return toResource(store.require(conversationId));
  }

  /**
   * Run citation ask and append USER + ASSISTANT turns. Creates a conversation when {@code
   * conversationId} is null/blank (also used by {@code POST /v1/queries:ask}).
   */
  public QueryResource ask(String conversationId, QueryRequest request) {
    boolean hasSeed = hasSeed(request);
    boolean hasFollowUp =
        request != null && request.followUp() != null && !request.followUp().isBlank();

    if (!hasSeed && !hasFollowUp) {
      throw new ValidationException(
          "Provide accusedId or firId, or a followUp with conversationId");
    }

    Conversation conversation = resolveConversation(conversationId, request, hasSeed, hasFollowUp);

    String priorTurns =
        ConversationHistoryPacker.pack(
            conversation.messages(),
            queryProperties.getHistoryMaxTurns(),
            queryProperties.getHistoryMaxChars(),
            queryProperties.getHistoryMaxTurnChars());
    if (!priorTurns.isBlank()) {
      log.info(
          "prompt packing conversationId={} historyChars={} maxTurns={}",
          conversation.conversationId(),
          priorTurns.length(),
          queryProperties.getHistoryMaxTurns());
    }

    // mvp2/12 Step F — similar-cases intent → PgVector ANN (before ACC/FIR seed resolve).
    if (!hasSeed && hasFollowUp && followUpResolver.isSimilarCasesIntent(request.followUp())) {
      String probeFir = followUpResolver.resolveSimilarProbeFir(conversation, request.followUp());
      log.info(
          "follow-up similar conversationId={} text='{}' → probeFir={}",
          conversation.conversationId(),
          truncate(request.followUp()),
          probeFir);
      QueryResource answer =
          queryService.askSimilar(
              probeFir, conversation.conversationId(), priorTurns, request.followUp());
      appendTurns(
          conversation,
          request.followUp().trim(),
          null,
          probeFir,
          answer);
      return answer;
    }

    QueryRequest effective = request;
    if (!hasSeed && hasFollowUp) {
      QuerySeed resolved = followUpResolver.resolve(conversation, request.followUp());
      log.info(
          "follow-up resolved conversationId={} text='{}' → {} {}",
          conversation.conversationId(),
          truncate(request.followUp()),
          resolved.kind(),
          resolved.entityId());
      effective =
          resolved.kind() == QuerySeedKind.ACCUSED
              ? new QueryRequest(
                  resolved.entityId(), null, conversation.conversationId(), request.followUp())
              : new QueryRequest(
                  null, resolved.entityId(), conversation.conversationId(), request.followUp());
    }

    QueryResource answer =
        queryService.ask(effective, conversation.conversationId(), priorTurns);

    String userText = userTurnText(effective, hasFollowUp ? request.followUp() : null);
    appendTurns(
        conversation,
        userText,
        blankToNull(effective == null ? null : effective.accusedId()),
        blankToNull(effective == null ? null : effective.firId()),
        answer);

    return answer;
  }

  private static void appendTurns(
      Conversation conversation,
      String userText,
      String accusedId,
      String firId,
      QueryResource answer) {
    List<RelatedEntityRef> relatedRefs = toRefs(answer.relatedEntities());

    conversation.append(
        new ConversationMessage(
            UUID.randomUUID().toString(),
            ConversationMessageRole.USER,
            Instant.now(),
            userText,
            accusedId,
            firId,
            null,
            List.of(),
            List.of(),
            List.of()));

    conversation.append(
        new ConversationMessage(
            UUID.randomUUID().toString(),
            ConversationMessageRole.ASSISTANT,
            Instant.now(),
            answer.answer(),
            accusedId,
            firId,
            answer.queryId(),
            answer.evidenceSources() == null ? List.of() : List.copyOf(answer.evidenceSources()),
            answer.relatedFirs() == null ? List.of() : List.copyOf(answer.relatedFirs()),
            relatedRefs));
  }

  private Conversation resolveConversation(
      String conversationId, QueryRequest request, boolean hasSeed, boolean hasFollowUp) {
    if (conversationId == null || conversationId.isBlank()) {
      if (hasFollowUp && !hasSeed) {
        if (hasUsableContext(request)) {
          log.info("follow-up without conversationId — hydrating from followUpContext");
          return hydrateFromContext(store.create(), request.followUpContext());
        }
        throw new ValidationException(
            "followUp requires conversationId (or followUpContext from the last answer)");
      }
      return store.create();
    }

    Optional<Conversation> existing = store.find(conversationId);
    if (existing.isPresent()) {
      return existing.get();
    }

    // Ephemeral store miss (AppSail recycle / another instance).
    if (hasFollowUp && hasUsableContext(request)) {
      log.warn(
          "conversation miss conversationId={} — hydrating from followUpContext",
          conversationId);
      return hydrateFromContext(store.create(conversationId), request.followUpContext());
    }
    if (hasSeed) {
      log.warn(
          "conversation miss conversationId={} — starting new thread for seeded ask",
          conversationId);
      return store.create();
    }
    throw new ResourceNotFoundException(
        "No conversation for conversationId="
            + conversationId
            + ". The session was lost (AppSail recycle). Ask the accused/FIR again, then retry the follow-up.");
  }

  private static boolean hasUsableContext(QueryRequest request) {
    if (request == null || request.followUpContext() == null) {
      return false;
    }
    FollowUpContext ctx = request.followUpContext();
    boolean hasEntities =
        (ctx.relatedEntities() != null && !ctx.relatedEntities().isEmpty())
            || (ctx.evidenceSources() != null && !ctx.evidenceSources().isEmpty())
            || (ctx.relatedFirs() != null && !ctx.relatedFirs().isEmpty());
    boolean hasSeed =
        (ctx.accusedId() != null && !ctx.accusedId().isBlank())
            || (ctx.firId() != null && !ctx.firId().isBlank());
    return hasEntities || hasSeed;
  }

  private static Conversation hydrateFromContext(Conversation conversation, FollowUpContext ctx) {
    List<String> evidence =
        ctx.evidenceSources() == null ? List.of() : List.copyOf(ctx.evidenceSources());
    List<String> firs = ctx.relatedFirs() == null ? List.of() : List.copyOf(ctx.relatedFirs());
    List<RelatedEntityRef> related = toRefs(ctx.relatedEntities());
    conversation.append(
        new ConversationMessage(
            UUID.randomUUID().toString(),
            ConversationMessageRole.ASSISTANT,
            Instant.now(),
            "(prior answer citations)",
            blankToNull(ctx.accusedId()),
            blankToNull(ctx.firId()),
            null,
            evidence,
            firs,
            related));
    return conversation;
  }

  private static boolean hasSeed(QueryRequest request) {
    if (request == null) {
      return false;
    }
    boolean accused = request.accusedId() != null && !request.accusedId().isBlank();
    boolean fir = request.firId() != null && !request.firId().isBlank();
    return accused || fir;
  }

  private static String userTurnText(QueryRequest request, String followUpOverride) {
    if (followUpOverride != null && !followUpOverride.isBlank()) {
      return followUpOverride.trim();
    }
    if (request == null) {
      return "(empty ask)";
    }
    if (request.accusedId() != null && !request.accusedId().isBlank()) {
      return "Ask about accused " + request.accusedId().trim();
    }
    if (request.firId() != null && !request.firId().isBlank()) {
      return "Ask about FIR " + request.firId().trim();
    }
    return "(empty ask)";
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static String truncate(String text) {
    String t = text == null ? "" : text.trim();
    return t.length() <= 80 ? t : t.substring(0, 77) + "...";
  }

  private static List<RelatedEntityRef> toRefs(List<RelatedEntityResource> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    return entities.stream()
        .filter(e -> e != null && e.id() != null && !e.id().isBlank())
        .map(e -> new RelatedEntityRef(e.id(), e.type(), e.label()))
        .toList();
  }

  private static ConversationResource toResource(Conversation conversation) {
    List<ConversationMessageResource> messages =
        conversation.messages().stream().map(ConversationService::toMessageResource).toList();
    return new ConversationResource(
        conversation.conversationId(), conversation.createdAt(), messages);
  }

  private static ConversationMessageResource toMessageResource(ConversationMessage message) {
    List<RelatedEntityResource> entities =
        message.relatedEntities() == null
            ? List.of()
            : message.relatedEntities().stream()
                .map(r -> new RelatedEntityResource(r.id(), r.type(), r.label()))
                .toList();
    return new ConversationMessageResource(
        message.messageId(),
        message.role().name(),
        message.createdAt(),
        message.text(),
        message.accusedId(),
        message.firId(),
        message.queryId(),
        message.evidenceSources(),
        message.relatedFirs(),
        entities);
  }
}

package dev.aparadhkavach.orchestration.query.conversation;

import java.time.Instant;
import java.util.List;

/**
 * One stored turn (mvp2/12 Step A/B). Assistant turns keep citation + related-entity snapshot for
 * the follow-up resolver.
 */
public record ConversationMessage(
    String messageId,
    ConversationMessageRole role,
    Instant createdAt,
    String text,
    String accusedId,
    String firId,
    String queryId,
    List<String> evidenceSources,
    List<String> relatedFirs,
    List<RelatedEntityRef> relatedEntities) {}

package dev.aparadhkavach.orchestration.dto;

import java.time.Instant;
import java.util.List;

/** One turn in {@link ConversationResource} (mvp2/12 Step A/B). */
public record ConversationMessageResource(
    String messageId,
    String role,
    Instant createdAt,
    String text,
    String accusedId,
    String firId,
    String queryId,
    List<String> evidenceSources,
    List<String> relatedFirs,
    List<RelatedEntityResource> relatedEntities) {}

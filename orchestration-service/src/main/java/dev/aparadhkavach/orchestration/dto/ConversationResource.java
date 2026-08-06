package dev.aparadhkavach.orchestration.dto;

import java.time.Instant;
import java.util.List;

/** Full conversation thread for {@code GET /v1/conversations/{id}} (mvp2/12 Step A). */
public record ConversationResource(
    String conversationId, Instant createdAt, List<ConversationMessageResource> messages) {}

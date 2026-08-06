package dev.aparadhkavach.orchestration.dto;

import java.time.Instant;

/** Response for {@code POST /v1/conversations} (mvp2/12 Step A). */
public record ConversationCreatedResource(String conversationId, Instant createdAt) {}

package dev.aparadhkavach.orchestration.dto;

import java.util.List;

/**
 * Conversational Q&amp;A envelope (Design &amp; Schema §6.6 / mvp2/11). Not a plain Analytics-style
 * resource.
 */
public record QueryResource(
    String queryId,
    String conversationId,
    String answer,
    List<String> evidenceSources,
    List<String> relatedFirs,
    List<RelatedEntityResource> relatedEntities,
    double confidenceScore,
    String reasoningSummary,
    long latencyMs) {}

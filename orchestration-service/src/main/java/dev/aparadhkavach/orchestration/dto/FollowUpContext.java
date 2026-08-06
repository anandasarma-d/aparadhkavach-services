package dev.aparadhkavach.orchestration.dto;

import java.util.List;

/**
 * Client-held citation snapshot for follow-ups when the in-memory conversation store misses the
 * thread (AppSail recycle / multi-instance). mvp2/12 Step B resilience.
 */
public record FollowUpContext(
    String accusedId,
    String firId,
    List<String> evidenceSources,
    List<String> relatedFirs,
    List<RelatedEntityResource> relatedEntities) {}

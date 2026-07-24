package dev.aparadhkavach.analytics.dto;

import java.util.List;

/**
 * Section 6.7 list shape for {@code GET /v1/analytics/hotspots} — plain resource, no ADR-020
 * conversational envelope.
 */
public record HotspotsPageResource(List<HotspotResource> hotspots, String nextPageToken) {}

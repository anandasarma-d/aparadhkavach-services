package dev.aparadhkavach.orchestration.dto;

/** One related entity cited in a conversational Q&amp;A response (ADR-020 Claude envelope). */
public record RelatedEntityResource(String id, String type, String label) {}

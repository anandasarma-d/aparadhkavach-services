package dev.aparadhkavach.orchestration.dto;

/**
 * Single-shot Q&amp;A input (mvp2/11). Exactly one of {@code accusedId} / {@code firId} must be
 * non-blank.
 */
public record QueryRequest(String accusedId, String firId) {}

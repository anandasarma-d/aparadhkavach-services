package dev.aparadhkavach.orchestration.dto;

/**
 * Q&amp;A input (mvp2/11 + mvp2/12 Step A/B). Provide exactly one of {@code accusedId} / {@code
 * firId}, <strong>or</strong> a {@code followUp} string with {@code conversationId} (and ideally
 * {@code followUpContext} so a store miss can still resolve).
 */
public record QueryRequest(
    String accusedId,
    String firId,
    String conversationId,
    String followUp,
    FollowUpContext followUpContext) {
  public QueryRequest(String accusedId, String firId) {
    this(accusedId, firId, null, null, null);
  }

  public QueryRequest(String accusedId, String firId, String conversationId) {
    this(accusedId, firId, conversationId, null, null);
  }

  public QueryRequest(String accusedId, String firId, String conversationId, String followUp) {
    this(accusedId, firId, conversationId, followUp, null);
  }
}

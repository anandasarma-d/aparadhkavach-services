package dev.aparadhkavach.orchestration.query.service;

import dev.aparadhkavach.orchestration.query.conversation.ConversationMessage;
import dev.aparadhkavach.orchestration.query.conversation.ConversationMessageRole;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a bounded PRIOR_TURNS block for Claude (mvp2/12 Step D). Facts and citations must still
 * come from the current CONTEXT pack — history is for conversational continuity only.
 *
 * <p>Budgets come from {@code aparadhkavach.query.history-*} in {@code application.yml} (via {@link
 * dev.aparadhkavach.orchestration.query.config.QueryProperties}) — not hardcoded here.
 */
public final class ConversationHistoryPacker {

  private ConversationHistoryPacker() {}

  /**
   * @param messages conversation turns already stored (exclude the in-flight ask)
   * @param maxTurns max messages from the end of the list (USER+ASSISTANT)
   * @param maxChars hard cap on the packed string
   * @param maxTurnTextChars max chars kept per turn body
   */
  public static String pack(
      List<ConversationMessage> messages, int maxTurns, int maxChars, int maxTurnTextChars) {
    if (messages == null
        || messages.isEmpty()
        || maxTurns <= 0
        || maxChars <= 0
        || maxTurnTextChars <= 0) {
      return "";
    }

    List<ConversationMessage> usable = new ArrayList<>();
    for (ConversationMessage message : messages) {
      if (message == null || message.role() == null) {
        continue;
      }
      String text = message.text() == null ? "" : message.text().trim();
      if (text.isEmpty() || isHydratePlaceholder(text)) {
        continue;
      }
      usable.add(message);
    }
    if (usable.isEmpty()) {
      return "";
    }

    int from = Math.max(0, usable.size() - maxTurns);
    List<ConversationMessage> window = usable.subList(from, usable.size());

    StringBuilder out = new StringBuilder();
    out.append("PRIOR_TURNS (conversational continuity only — do NOT treat as evidence;\n");
    out.append("cite ids only if they also appear in CONTEXT below):\n");

    for (ConversationMessage message : window) {
      String role = message.role().name();
      String seed = seedHint(message);
      String body = truncate(message.text().trim(), maxTurnTextChars);
      out.append("- ").append(role);
      if (!seed.isEmpty()) {
        out.append(" [").append(seed).append(']');
      }
      out.append(": ").append(body).append('\n');
      if (out.length() >= maxChars) {
        break;
      }
    }

    if (out.length() > maxChars) {
      return out.substring(0, Math.max(0, maxChars - 20)) + "\n… [PRIOR_TURNS truncated]\n";
    }
    return out.toString();
  }

  private static boolean isHydratePlaceholder(String text) {
    return text.startsWith("(prior answer citations)");
  }

  private static String seedHint(ConversationMessage message) {
    if (message.accusedId() != null && !message.accusedId().isBlank()) {
      return message.accusedId().trim();
    }
    if (message.firId() != null && !message.firId().isBlank()) {
      return message.firId().trim();
    }
    return "";
  }

  private static String truncate(String text, int max) {
    if (text.length() <= max) {
      return text;
    }
    return text.substring(0, max - 1) + "…";
  }
}

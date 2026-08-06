package dev.aparadhkavach.orchestration.query.service;

import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.query.conversation.Conversation;
import dev.aparadhkavach.orchestration.query.conversation.ConversationMessage;
import dev.aparadhkavach.orchestration.query.conversation.ConversationMessageRole;
import dev.aparadhkavach.orchestration.query.conversation.RelatedEntityRef;
import dev.aparadhkavach.orchestration.query.model.QuerySeed;
import dev.aparadhkavach.orchestration.query.model.QuerySeedKind;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Maps a free-text follow-up to an ACC-/FIR- seed using prior turn citations (mvp2/12 Step B).
 * Deterministic heuristics only — no extra Claude call.
 */
@Component
public class FollowUpResolver {

  private static final Pattern ENTITY_ID =
      Pattern.compile("\\b((?:ACC|FIR)-[A-Za-z0-9_-]+)\\b", Pattern.CASE_INSENSITIVE);

  public QuerySeed resolve(Conversation conversation, String followUpText) {
    if (followUpText == null || followUpText.isBlank()) {
      throw new ValidationException("followUp text is required when accusedId/firId are omitted");
    }
    String text = followUpText.trim();
    CitationPool pool = CitationPool.from(conversation);
    if (pool.isEmpty() && pool.lastSeed() == null) {
      throw new ValidationException(
          "No prior citations in this conversation to resolve the follow-up against. Ask with an ACC-/FIR- id first.");
    }

    // 1) Explicit ACC-/FIR- id only if it appears in prior citations (not any random id).
    Matcher matcher = ENTITY_ID.matcher(text);
    List<String> mentioned = new ArrayList<>();
    while (matcher.find()) {
      mentioned.add(matcher.group(1).toUpperCase(Locale.ROOT));
    }
    for (String id : mentioned) {
      if (pool.contains(id)) {
        return toSeed(id);
      }
    }
    if (!mentioned.isEmpty()) {
      throw new ValidationException(
          "Follow-up names "
              + String.join(", ", mentioned)
              + " but that id is not in this thread’s citations. "
              + "Use a cited id such as "
              + pool.hintIds()
              + ", or Ask that id as a new seed (New thread).");
    }

    String lower = text.toLowerCase(Locale.ROOT);

    // 2) Co-accused / linked people → first ACC- from related entities / evidence
    if (mentionsCoAccused(lower)) {
      String acc = pool.firstAccusedNeighbor();
      if (acc != null) {
        return new QuerySeed(QuerySeedKind.ACCUSED, acc);
      }
    }

    // 3) That case / linked FIR → first FIR- from related / evidence
    if (mentionsLinkedFir(lower)) {
      String fir = pool.firstFirNeighbor();
      if (fir != null) {
        return new QuerySeed(QuerySeedKind.FIR, fir);
      }
    }

    // 4) Label substring match against related entity labels
    String byLabel = pool.matchLabel(lower);
    if (byLabel != null) {
      return toSeed(byLabel);
    }

    // 5) Same / this accused|FIR → last seed
    if (mentionsSameSubject(lower) && pool.lastSeed() != null) {
      return pool.lastSeed();
    }

    // 6) Fallback: last seed if any
    if (pool.lastSeed() != null) {
      return pool.lastSeed();
    }

    throw new ValidationException(
        "Could not resolve follow-up to an accused or FIR from prior citations. "
            + "Try naming an id such as "
            + pool.hintIds()
            + ".");
  }

  private static boolean mentionsSameSubject(String lower) {
    return lower.contains("same accused")
        || lower.contains("same fir")
        || lower.contains("same case")
        || lower.contains("this accused")
        || lower.contains("this fir")
        || lower.contains("this case")
        || lower.contains("ask again")
        || lower.matches(".*\\bagain\\b.*");
  }

  private static boolean mentionsCoAccused(String lower) {
    return lower.contains("co-accused")
        || lower.contains("coaccused")
        || lower.contains("co accused")
        || lower.contains("linked accused")
        || lower.contains("other accused")
        || lower.contains("those accused")
        || lower.contains("the accused");
  }

  private static boolean mentionsLinkedFir(String lower) {
    return lower.contains("linked fir")
        || lower.contains("that fir")
        || lower.contains("those fir")
        || lower.contains("related fir")
        || lower.contains("the case")
        || lower.contains("that case");
  }

  private static QuerySeed toSeed(String id) {
    String normalized = id.trim().toUpperCase(Locale.ROOT);
    if (normalized.startsWith("FIR-")) {
      return new QuerySeed(QuerySeedKind.FIR, normalized);
    }
    if (normalized.startsWith("ACC-")) {
      return new QuerySeed(QuerySeedKind.ACCUSED, normalized);
    }
    throw new ValidationException("Unsupported entity id in follow-up: " + id);
  }

  private static final class CitationPool {
    private final Set<String> ids = new LinkedHashSet<>();
    private final List<RelatedEntityRef> entities = new ArrayList<>();
    private QuerySeed lastSeed;

    static CitationPool from(Conversation conversation) {
      CitationPool pool = new CitationPool();
      for (ConversationMessage message : conversation.messages()) {
        if (message.accusedId() != null && !message.accusedId().isBlank()) {
          pool.lastSeed = new QuerySeed(QuerySeedKind.ACCUSED, message.accusedId().trim());
          pool.ids.add(message.accusedId().trim().toUpperCase(Locale.ROOT));
        }
        if (message.firId() != null && !message.firId().isBlank()) {
          pool.lastSeed = new QuerySeed(QuerySeedKind.FIR, message.firId().trim());
          pool.ids.add(message.firId().trim().toUpperCase(Locale.ROOT));
        }
        if (message.role() != ConversationMessageRole.ASSISTANT) {
          continue;
        }
        addAll(pool.ids, message.evidenceSources());
        addAll(pool.ids, message.relatedFirs());
        if (message.relatedEntities() != null) {
          for (RelatedEntityRef ref : message.relatedEntities()) {
            if (ref != null && ref.id() != null && !ref.id().isBlank()) {
              pool.ids.add(ref.id().trim().toUpperCase(Locale.ROOT));
              pool.entities.add(ref);
            }
          }
        }
      }
      return pool;
    }

    private static void addAll(Set<String> target, List<String> values) {
      if (values == null) {
        return;
      }
      for (String value : values) {
        if (value != null && !value.isBlank()) {
          target.add(value.trim().toUpperCase(Locale.ROOT));
        }
      }
    }

    boolean isEmpty() {
      return ids.isEmpty();
    }

    boolean contains(String id) {
      return ids.contains(id.toUpperCase(Locale.ROOT));
    }

    QuerySeed lastSeed() {
      return lastSeed;
    }

    String firstAccusedNeighbor() {
      for (RelatedEntityRef ref : entities) {
        if (ref.id() != null && ref.id().toUpperCase(Locale.ROOT).startsWith("ACC-")) {
          if (lastSeed != null
              && lastSeed.kind() == QuerySeedKind.ACCUSED
              && lastSeed.entityId().equalsIgnoreCase(ref.id())) {
            continue;
          }
          return ref.id().trim().toUpperCase(Locale.ROOT);
        }
      }
      for (String id : ids) {
        if (id.startsWith("ACC-")
            && (lastSeed == null
                || lastSeed.kind() != QuerySeedKind.ACCUSED
                || !lastSeed.entityId().equalsIgnoreCase(id))) {
          return id;
        }
      }
      return null;
    }

    String firstFirNeighbor() {
      for (String id : ids) {
        if (id.startsWith("FIR-")
            && (lastSeed == null
                || lastSeed.kind() != QuerySeedKind.FIR
                || !lastSeed.entityId().equalsIgnoreCase(id))) {
          return id;
        }
      }
      for (String id : ids) {
        if (id.startsWith("FIR-")) {
          return id;
        }
      }
      return null;
    }

    String matchLabel(String lowerFollowUp) {
      RelatedEntityRef best = null;
      int bestLen = 0;
      for (RelatedEntityRef ref : entities) {
        if (ref.label() == null || ref.label().isBlank() || ref.id() == null) {
          continue;
        }
        String label = ref.label().trim().toLowerCase(Locale.ROOT);
        if (label.length() < 3) {
          continue;
        }
        if (lowerFollowUp.contains(label) && label.length() > bestLen) {
          best = ref;
          bestLen = label.length();
        }
      }
      return best == null ? null : best.id().trim().toUpperCase(Locale.ROOT);
    }

    String hintIds() {
      List<String> hints = new ArrayList<>();
      for (String id : ids) {
        hints.add(id);
        if (hints.size() >= 3) {
          break;
        }
      }
      return hints.isEmpty() ? "ACC-… / FIR-…" : String.join(", ", hints);
    }
  }
}

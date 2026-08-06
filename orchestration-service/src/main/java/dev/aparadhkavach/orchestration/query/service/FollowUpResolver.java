package dev.aparadhkavach.orchestration.query.service;

import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.query.conversation.Conversation;
import dev.aparadhkavach.orchestration.query.conversation.ConversationMessage;
import dev.aparadhkavach.orchestration.query.conversation.ConversationMessageRole;
import dev.aparadhkavach.orchestration.query.conversation.RelatedEntityRef;
import dev.aparadhkavach.orchestration.query.model.QuerySeed;
import dev.aparadhkavach.orchestration.query.model.QuerySeedKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Maps a free-text follow-up to an ACC-/FIR- seed using prior turn citations (mvp2/12 Step B).
 * Deterministic heuristics only — no extra Claude call. Ask seeds remain ACC-/FIR- only; cited
 * VEH-/LOC-/… ids resolve to the host accused/FIR that mentioned them.
 */
@Component
public class FollowUpResolver {

  private static final Pattern ENTITY_ID =
      Pattern.compile(
          "\\b((?:ACC|FIR|VEH|LOC|VIC|WIT|OFF)-[A-Za-z0-9_-]+)\\b", Pattern.CASE_INSENSITIVE);

  /** Common Karnataka-style registration fragments in officer text. */
  private static final Pattern PLATE =
      Pattern.compile(
          "\\b(KA\\s*-?\\s*\\d{1,2}\\s*-?\\s*[A-Z]{1,3}\\s*-?\\s*\\d{1,4})\\b",
          Pattern.CASE_INSENSITIVE);

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

    // 1) Explicit entity id — ACC/FIR direct; other cited types → host ACC/FIR.
    Matcher matcher = ENTITY_ID.matcher(text);
    List<String> mentioned = new ArrayList<>();
    while (matcher.find()) {
      mentioned.add(matcher.group(1).toUpperCase(Locale.ROOT));
    }
    for (String id : mentioned) {
      QuerySeed seed = pool.toAskSeed(id);
      if (seed != null) {
        return seed;
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

    // 2) Registration plate / vehicle id via label (handles minor OCR typos via normalized match)
    if (mentionsPlate(text)) {
      QuerySeed byPlate = pool.matchPlate(text);
      if (byPlate != null) {
        return byPlate;
      }
      if (mentionsVehicle(lower)) {
        QuerySeed vehicleHost = pool.firstVehicleHost();
        if (vehicleHost != null) {
          return vehicleHost;
        }
      }
      throw new ValidationException(
          "Follow-up names a vehicle registration that is not in this thread’s citations. "
              + "Use a plate or VEH- id from a prior answer, or ask the owning ACC-/FIR- such as "
              + pool.hintIds()
              + ".");
    }

    // 3) “the vehicle / number plate …” → host seed of a cited VEH-
    if (mentionsVehicle(lower)) {
      QuerySeed vehicleHost = pool.firstVehicleHost();
      if (vehicleHost != null) {
        return vehicleHost;
      }
      throw new ValidationException(
          "This thread has no cited vehicle to follow up on. "
              + "Ask an accused/FIR that mentions a vehicle, or name a cited ACC-/FIR- id such as "
              + pool.hintIds()
              + ".");
    }

    // 4) Co-accused / linked people → first ACC- from related entities / evidence
    if (mentionsCoAccused(lower)) {
      String acc = pool.firstAccusedNeighbor();
      if (acc != null) {
        return new QuerySeed(QuerySeedKind.ACCUSED, acc);
      }
    }

    // 5) That case / linked FIR → first FIR- from related / evidence
    if (mentionsLinkedFir(lower)) {
      String fir = pool.firstFirNeighbor();
      if (fir != null) {
        return new QuerySeed(QuerySeedKind.FIR, fir);
      }
    }

    // 6) Label substring match against related entity labels → ask seed (host if needed)
    String byLabel = pool.matchLabel(lower);
    if (byLabel != null) {
      QuerySeed seed = pool.toAskSeed(byLabel);
      if (seed != null) {
        return seed;
      }
    }

    // 7) Same / this accused|FIR → last seed
    if (mentionsSameSubject(lower) && pool.lastSeed() != null) {
      return pool.lastSeed();
    }

    // 8) Fallback: last seed only for vague follow-ups — never after a specific unsupported topic
    if (pool.lastSeed() != null && !looksLikeSpecificUnsupportedTopic(lower)) {
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

  private static boolean mentionsPlate(String text) {
    return PLATE.matcher(text).find();
  }

  private static boolean mentionsVehicle(String lower) {
    return lower.contains("vehicle")
        || lower.contains("veh-")
        || lower.contains("number plate")
        || lower.contains("registration")
        || lower.contains("the car")
        || lower.contains("the bike")
        || lower.contains("the scooter");
  }

  /** Topics we must not silently remap to the last ACC/FIR (caused duplicate briefings). */
  private static boolean looksLikeSpecificUnsupportedTopic(String lower) {
    return mentionsVehicle(lower)
        || lower.contains("location")
        || lower.contains("witness")
        || lower.contains("victim")
        || lower.contains("officer")
        || lower.contains("phone")
        || lower.contains("weapon");
  }

  private static final class CitationPool {
    private final Set<String> ids = new LinkedHashSet<>();
    private final List<RelatedEntityRef> entities = new ArrayList<>();
    /** Cited entity id → ACC/FIR seed from the turn that mentioned it. */
    private final Map<String, QuerySeed> hostByEntityId = new LinkedHashMap<>();
    private QuerySeed lastSeed;

    static CitationPool from(Conversation conversation) {
      CitationPool pool = new CitationPool();
      for (ConversationMessage message : conversation.messages()) {
        QuerySeed messageHost = hostOf(message);
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
        addAll(pool, message.evidenceSources(), messageHost);
        addAll(pool, message.relatedFirs(), messageHost);
        if (message.relatedEntities() != null) {
          for (RelatedEntityRef ref : message.relatedEntities()) {
            if (ref != null && ref.id() != null && !ref.id().isBlank()) {
              String id = ref.id().trim().toUpperCase(Locale.ROOT);
              pool.ids.add(id);
              pool.entities.add(ref);
              if (messageHost != null) {
                pool.hostByEntityId.putIfAbsent(id, messageHost);
              }
            }
          }
        }
      }
      return pool;
    }

    private static QuerySeed hostOf(ConversationMessage message) {
      if (message.accusedId() != null && !message.accusedId().isBlank()) {
        return new QuerySeed(QuerySeedKind.ACCUSED, message.accusedId().trim());
      }
      if (message.firId() != null && !message.firId().isBlank()) {
        return new QuerySeed(QuerySeedKind.FIR, message.firId().trim());
      }
      return null;
    }

    private static void addAll(CitationPool pool, List<String> values, QuerySeed host) {
      if (values == null) {
        return;
      }
      for (String value : values) {
        if (value != null && !value.isBlank()) {
          String id = value.trim().toUpperCase(Locale.ROOT);
          pool.ids.add(id);
          if (host != null) {
            pool.hostByEntityId.putIfAbsent(id, host);
          }
        }
      }
    }

    boolean isEmpty() {
      return ids.isEmpty();
    }

    QuerySeed lastSeed() {
      return lastSeed;
    }

    /**
     * ACC-/FIR- → direct seed. Other cited ids (VEH-, …) → host ACC/FIR from the turn that cited
     * them. Missing / uncited → null.
     */
    QuerySeed toAskSeed(String rawId) {
      String id = rawId.trim().toUpperCase(Locale.ROOT);
      if (!ids.contains(id)) {
        return null;
      }
      if (id.startsWith("FIR-")) {
        return new QuerySeed(QuerySeedKind.FIR, id);
      }
      if (id.startsWith("ACC-")) {
        return new QuerySeed(QuerySeedKind.ACCUSED, id);
      }
      QuerySeed host = hostByEntityId.get(id);
      if (host != null) {
        return host;
      }
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

    QuerySeed firstVehicleHost() {
      for (RelatedEntityRef ref : entities) {
        if (ref.id() == null) {
          continue;
        }
        String id = ref.id().trim().toUpperCase(Locale.ROOT);
        if (id.startsWith("VEH-") || isVehicleType(ref.type())) {
          QuerySeed host = toAskSeed(id);
          if (host != null) {
            return host;
          }
        }
      }
      for (String id : ids) {
        if (id.startsWith("VEH-")) {
          QuerySeed host = toAskSeed(id);
          if (host != null) {
            return host;
          }
        }
      }
      return null;
    }

    QuerySeed matchPlate(String followUpText) {
      Matcher plates = PLATE.matcher(followUpText);
      List<String> mentioned = new ArrayList<>();
      while (plates.find()) {
        mentioned.add(normalizePlate(plates.group(1)));
      }
      if (mentioned.isEmpty()) {
        return null;
      }
      RelatedEntityRef best = null;
      int bestScore = 0;
      for (RelatedEntityRef ref : entities) {
        if (ref.label() == null || ref.label().isBlank() || ref.id() == null) {
          continue;
        }
        String labelNorm = normalizePlate(ref.label());
        if (labelNorm.isEmpty()) {
          continue;
        }
        for (String plate : mentioned) {
          int score = plateOverlapScore(plate, labelNorm);
          if (score > bestScore) {
            bestScore = score;
            best = ref;
          }
        }
      }
      // Require a meaningful overlap (full match or near-typo on the letter group).
      if (best == null || bestScore < 2) {
        return null;
      }
      return toAskSeed(best.id());
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
        // Avoid matching short plate fragments like "ka-16" alone via generic label path —
        // plates go through matchPlate.
        if (normalizePlate(label).startsWith("KA") && normalizePlate(label).length() >= 6) {
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
        if (id.startsWith("ACC-") || id.startsWith("FIR-")) {
          hints.add(id);
        }
        if (hints.size() >= 3) {
          break;
        }
      }
      if (hints.isEmpty()) {
        for (String id : ids) {
          hints.add(id);
          if (hints.size() >= 3) {
            break;
          }
        }
      }
      return hints.isEmpty() ? "ACC-… / FIR-…" : String.join(", ", hints);
    }

    private static boolean isVehicleType(String type) {
      return type != null && type.toLowerCase(Locale.ROOT).contains("vehicle");
    }

    private static String normalizePlate(String raw) {
      if (raw == null) {
        return "";
      }
      return raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Score plate similarity: 3 exact, 2 same length with ≤1 char diff (QO vs QQ), else 0/1 weak.
     */
    private static int plateOverlapScore(String a, String b) {
      if (a.isEmpty() || b.isEmpty()) {
        return 0;
      }
      if (a.equals(b)) {
        return 3;
      }
      if (a.length() == b.length()) {
        int diffs = 0;
        for (int i = 0; i < a.length(); i++) {
          if (a.charAt(i) != b.charAt(i)) {
            diffs++;
          }
        }
        if (diffs <= 1) {
          return 2;
        }
      }
      if (a.contains(b) || b.contains(a)) {
        return 1;
      }
      return 0;
    }
  }
}

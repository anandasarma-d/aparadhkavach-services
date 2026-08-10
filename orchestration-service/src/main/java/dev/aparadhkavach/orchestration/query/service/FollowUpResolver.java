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
 * Deterministic heuristics only — no extra model call. Ask seeds remain ACC-/FIR- only; cited
 * VEH-/plates resolve to the host accused/FIR. LOC- and location/place intents are refused until
 * a dedicated retrieval path exists (D-107).
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

    String lower = text.toLowerCase(Locale.ROOT);

    // 1) Explicit entity id — ACC/FIR direct; VEH- → host; LOC-/other → refuse (D-106/D-107).
    Matcher matcher = ENTITY_ID.matcher(text);
    List<String> mentioned = new ArrayList<>();
    while (matcher.find()) {
      mentioned.add(matcher.group(1).toUpperCase(Locale.ROOT));
    }
    for (String id : mentioned) {
      if (id.startsWith("LOC-")) {
        throw new ValidationException(LOCATION_FOLLOW_UP_REFUSE);
      }
      if (id.startsWith("VEH-")) {
        QuerySeed seed = pool.toAskSeed(id);
        if (seed != null) {
          return seed;
        }
        continue;
      }
      if (id.startsWith("ACC-") || id.startsWith("FIR-")) {
        QuerySeed seed = pool.toAskSeed(id);
        if (seed != null) {
          return seed;
        }
        continue;
      }
      // VIC-/WIT-/OFF-/… — do not silently remap to host (wrong briefing).
      if (pool.containsId(id)) {
        throw new ValidationException(
            "Follow-up on "
                + id
                + " is not supported on this path yet. Ask about a cited accused or FIR, or tap an ACC-/FIR- citation.");
      }
    }
    if (!mentioned.isEmpty()) {
      throw new ValidationException(
          "Follow-up names "
              + String.join(", ", mentioned)
              + " but that id is not among the citations for this ask. "
              + "Tap a cited ACC-/FIR- chip, or start a New thread with that id.");
    }

    // 1b) Similar-cases intent is routed by ConversationService → askSimilar (Step F).
    // If resolve() sees it, refuse lastSeed remap and require a cited FIR probe message.
    if (mentionsSimilarCases(lower)) {
      if (hasLocationConstraint(lower) || mentionedContainsLoc(mentioned)) {
        throw new ValidationException(LOCATION_FOLLOW_UP_REFUSE);
      }
      throw new ValidationException(
          "Similar-cases follow-ups need a cited FIR to probe. "
              + "Ask about an FIR first (or tap a Related FIR), then ask for similar cases.");
    }

    // 1c) Location / “cases at this place” intents — refuse before lastSeed remap (D-107).
    if (mentionsLocationTopic(lower)) {
      throw new ValidationException(LOCATION_FOLLOW_UP_REFUSE);
    }

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
          "That vehicle registration is not cited in the current answer.");
    }

    // 3) “the vehicle / number plate …” → host seed of a cited VEH-
    if (mentionsVehicle(lower)) {
      QuerySeed vehicleHost = pool.firstVehicleHost();
      if (vehicleHost != null) {
        return vehicleHost;
      }
      throw new ValidationException(noCitedVehicleMessage(pool.lastSeed()));
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
      if (byLabel.startsWith("LOC-") || pool.isLocationEntity(byLabel)) {
        throw new ValidationException(LOCATION_FOLLOW_UP_REFUSE);
      }
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
        "Could not resolve that follow-up from the current answer. "
            + "Try a cited ACC-/FIR- id, or tap a citation chip.");
  }

  private static final String LOCATION_FOLLOW_UP_REFUSE =
      "Location and “cases at this place” follow-ups are not supported on this path yet. "
          + "Ask about a cited accused or FIR from the current answer.";

  private static final String NO_SIMILAR_PROBE =
      "No cited FIR is available to search similar cases from. "
          + "Ask about an FIR first, or tap a Related FIR chip, then ask again.";

  /**
   * mvp2/12 Step F — officer wants nearest FIRs by narrative embedding (not a graph re-ask).
   * Location-constrained “similar at this place” stays refuse (D-107).
   */
  public boolean isSimilarCasesIntent(String followUpText) {
    if (followUpText == null || followUpText.isBlank()) {
      return false;
    }
    String lower = followUpText.toLowerCase(Locale.ROOT);
    if (!mentionsSimilarCases(lower)) {
      return false;
    }
    return !hasLocationConstraint(lower) && !textMentionsLocId(followUpText);
  }

  /**
   * Pick a cited FIR probe for Step F ANN. Prefers an explicit cited FIR in the text, else last FIR
   * seed, else first related FIR in the citation pool.
   */
  public String resolveSimilarProbeFir(Conversation conversation, String followUpText) {
    if (followUpText == null || followUpText.isBlank()) {
      throw new ValidationException("followUp text is required for similar-cases intent");
    }
    String lower = followUpText.toLowerCase(Locale.ROOT);
    if (hasLocationConstraint(lower) || textMentionsLocId(followUpText)) {
      throw new ValidationException(LOCATION_FOLLOW_UP_REFUSE);
    }
    CitationPool pool = CitationPool.from(conversation);
    if (pool.isEmpty() && pool.lastSeed() == null) {
      throw new ValidationException(NO_SIMILAR_PROBE);
    }

    Matcher matcher = ENTITY_ID.matcher(followUpText);
    List<String> mentionedFirs = new ArrayList<>();
    while (matcher.find()) {
      String id = matcher.group(1).toUpperCase(Locale.ROOT);
      if (id.startsWith("FIR-")) {
        mentionedFirs.add(id);
      }
      if (id.startsWith("LOC-")) {
        throw new ValidationException(LOCATION_FOLLOW_UP_REFUSE);
      }
    }
    for (String fir : mentionedFirs) {
      if (pool.containsId(fir)) {
        return fir;
      }
    }
    if (!mentionedFirs.isEmpty()) {
      throw new ValidationException(
          "Follow-up names "
              + String.join(", ", mentionedFirs)
              + " but that FIR is not among the citations for this ask. "
              + "Tap a cited FIR chip, then ask for similar cases.");
    }

    if (pool.lastSeed() != null && pool.lastSeed().kind() == QuerySeedKind.FIR) {
      return pool.lastSeed().entityId();
    }
    String related = pool.firstFirNeighbor();
    if (related != null) {
      return related;
    }
    throw new ValidationException(NO_SIMILAR_PROBE);
  }

  private static String noCitedVehicleMessage(QuerySeed lastSeed) {
    if (lastSeed != null && lastSeed.kind() == QuerySeedKind.FIR) {
      return "This FIR has no cited vehicle in the current answer.";
    }
    return "This accused has no cited vehicle in the current answer.";
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

  private static boolean mentionsSimilarCases(String lower) {
    return lower.contains("similar case")
        || lower.contains("similar fir")
        || lower.contains("cases like this")
        || lower.contains("case like this")
        || lower.contains("like this story")
        || lower.contains("like this narrative")
        || lower.contains("find similar")
        || lower.contains("semantically similar")
        || (lower.contains("similar")
            && (lower.contains("fir") || lower.contains("case") || lower.contains("cases")));
  }

  private static boolean hasLocationConstraint(String lower) {
    return lower.contains("location")
        || lower.contains("same place")
        || lower.contains("same area")
        || lower.contains("same locality")
        || lower.contains("at that place")
        || lower.contains("in that area")
        || lower.contains("in the same location")
        || lower.contains("cases in the same")
        || lower.contains("cases at this")
        || lower.contains("cases at that")
        || lower.contains("other cases at")
        || lower.contains("loc-");
  }

  private static boolean textMentionsLocId(String text) {
    Matcher matcher = ENTITY_ID.matcher(text);
    while (matcher.find()) {
      if (matcher.group(1).toUpperCase(Locale.ROOT).startsWith("LOC-")) {
        return true;
      }
    }
    return false;
  }

  private static boolean mentionedContainsLoc(List<String> mentioned) {
    for (String id : mentioned) {
      if (id.startsWith("LOC-")) {
        return true;
      }
    }
    return false;
  }

  private static boolean mentionsLocationTopic(String lower) {
    return hasLocationConstraint(lower)
        || lower.contains("any other cases")
        || (lower.contains("similar")
            && (lower.contains("location")
                || lower.contains("place")
                || lower.contains("area")
                || lower.contains("loc-")));
  }

  /** Topics we must not silently remap to the last ACC/FIR (caused duplicate briefings). */
  private static boolean looksLikeSpecificUnsupportedTopic(String lower) {
    return mentionsVehicle(lower)
        || mentionsLocationTopic(lower)
        || mentionsSimilarCases(lower)
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

    boolean containsId(String id) {
      return ids.contains(id.trim().toUpperCase(Locale.ROOT));
    }

    boolean isLocationEntity(String id) {
      String upper = id.trim().toUpperCase(Locale.ROOT);
      if (upper.startsWith("LOC-")) {
        return true;
      }
      for (RelatedEntityRef ref : entities) {
        if (ref.id() != null
            && ref.id().equalsIgnoreCase(upper)
            && ref.type() != null
            && ref.type().toLowerCase(Locale.ROOT).contains("location")) {
          return true;
        }
      }
      return false;
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

package dev.aparadhkavach.orchestration.query.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aparadhkavach.commons.exception.ClaudeResponseValidationException;
import dev.aparadhkavach.orchestration.query.config.QueryProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * One non-streaming Claude call for F3 / mvp2/11. Returns structured JSON; refuses to invent facts
 * outside the assembled context. System prompt comes from {@link QueryProperties} (YAML / env).
 */
@Component
public class ClaudeQueryBridge {

  private static final Logger log = LoggerFactory.getLogger(ClaudeQueryBridge.class);

  /**
   * Wall-clock cap for the model call. Must stay under Catalyst AppSail ~30s after graph +
   * Investigation (~2s). HTTP connect/read are shorter via {@code ClaudeHttpTimeoutConfig}.
   */
  private static final long CLAUDE_TIMEOUT_SECONDS = 14;

  /** Keep CONTEXT short so Claude finishes a complete JSON object (FIR neighborhoods can be wide). */
  private static final int MAX_CONTEXT_CHARS = 6_000;

  private final ChatClient chatClient;
  private final ObjectMapper objectMapper;
  private final String anthropicApiKey;
  private final QueryProperties queryProperties;
  private final ExecutorService claudeExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "claude-query");
            t.setDaemon(true);
            return t;
          });

  /**
   * Production ctor — {@code @Autowired} is required because a private test ctor also exists;
   * without it Spring falls through to a missing no-arg constructor (AppSail boot failure).
   */
  @Autowired
  public ClaudeQueryBridge(
      ObjectProvider<ChatClient.Builder> chatClientBuilder,
      ObjectMapper objectMapper,
      QueryProperties queryProperties,
      @Value("${spring.ai.anthropic.api-key:}") String anthropicApiKey) {
    ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
    this.chatClient = builder != null ? builder.build() : null;
    this.objectMapper = objectMapper;
    this.queryProperties = queryProperties;
    this.anthropicApiKey = anthropicApiKey == null ? "" : anthropicApiKey;
    if (this.chatClient == null) {
      log.warn(
          "ChatClient.Builder not available — Q&A will return a graceful unavailable answer until"
              + " Spring AI Anthropic is configured");
    }
  }

  /** Test helper — parse / isConfigured without a live ChatClient. */
  static ClaudeQueryBridge forTests(ObjectMapper objectMapper, String anthropicApiKey) {
    return new ClaudeQueryBridge(objectMapper, anthropicApiKey, new QueryProperties());
  }

  private ClaudeQueryBridge(
      ObjectMapper objectMapper, String anthropicApiKey, QueryProperties queryProperties) {
    this.chatClient = null;
    this.objectMapper = objectMapper;
    this.queryProperties = queryProperties;
    this.anthropicApiKey = anthropicApiKey == null ? "" : anthropicApiKey;
  }

  public boolean isConfigured() {
    String key = anthropicApiKey.trim();
    return !key.isEmpty() && !key.contains("placeholder");
  }

  public ClaudeAnswer complete(String contextBlock, String seedEntityId) {
    return complete(contextBlock, seedEntityId, null, AskTask.GRAPH_BRIEFING);
  }

  /**
   * @param priorTurnsBlock optional PRIOR_TURNS text from {@link ConversationHistoryPacker} (Step D)
   */
  public ClaudeAnswer complete(String contextBlock, String seedEntityId, String priorTurnsBlock) {
    return complete(contextBlock, seedEntityId, priorTurnsBlock, AskTask.GRAPH_BRIEFING);
  }

  /**
   * @param task {@link AskTask#GRAPH_BRIEFING} (doc 11) or {@link AskTask#SIMILAR_CASES} (mvp2/12 Step
   *     F)
   */
  public ClaudeAnswer complete(
      String contextBlock, String seedEntityId, String priorTurnsBlock, AskTask task) {
    if (!isConfigured() || chatClient == null) {
      return unavailable(
          "Answer generation is not configured on this environment. "
              + "Context was assembled for "
              + seedEntityId
              + " but no model call was made.",
          seedEntityId);
    }

    String context = truncateContext(contextBlock);
    String history =
        priorTurnsBlock == null || priorTurnsBlock.isBlank() ? "" : priorTurnsBlock.trim() + "\n\n";
    AskTask effective = task == null ? AskTask.GRAPH_BRIEFING : task;
    String user =
        "Seed entity: "
            + seedEntityId
            + "\n\n"
            + history
            + "CONTEXT:\n"
            + context
            + "\n\nTask: "
            + effective.userTask()
            + " Humanize place codes (MARKET_AREA → market area). Reply with ONLY the JSON object"
            + " required by the system prompt — no markdown, no preamble.";

    long started = System.currentTimeMillis();
    log.info(
        "Claude ask start seed={} timeoutSec={} contextChars={} historyChars={}",
        seedEntityId,
        CLAUDE_TIMEOUT_SECONDS,
        context.length(),
        history.isEmpty() ? 0 : priorTurnsBlock.trim().length());
    Future<String> future =
        claudeExecutor.submit(
            () ->
                chatClient
                    .prompt()
                    .system(queryProperties.resolvedSystemPrompt())
                    .user(user)
                    .options(
                        AnthropicChatOptions.builder()
                            .temperature(0.0)
                            .maxTokens(2048)
                            .build())
                    .call()
                    .content());
    try {
      String raw = future.get(CLAUDE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      log.info(
          "Claude ask ok seed={} tookMs={}", seedEntityId, System.currentTimeMillis() - started);
      try {
        return parse(raw);
      } catch (ClaudeResponseValidationException ex) {
        // Ask path must not 502 the Gateway/UI — soft-fail with context-backed unavailable answer.
        log.warn(
            "Claude ask parse failed seed={} tookMs={}: {} rawSnippet={}",
            seedEntityId,
            System.currentTimeMillis() - started,
            ex.getMessage(),
            snippet(raw));
        return unavailable(
            "Could not produce a usable answer for "
                + seedEntityId
                + ". Context was assembled; try again shortly.",
            seedEntityId);
      }
    } catch (TimeoutException ex) {
      future.cancel(true);
      log.warn(
          "Claude ask timed out seed={} after {}s tookMs={}",
          seedEntityId,
          CLAUDE_TIMEOUT_SECONDS,
          System.currentTimeMillis() - started);
      return unavailable(
          "Answer timed out for "
              + seedEntityId
              + " after assembling context; try again shortly.",
          seedEntityId);
    } catch (ExecutionException ex) {
      Throwable root = unwrap(ex);
      if (root instanceof ClaudeResponseValidationException validation) {
        log.warn(
            "Claude ask parse failed (async) seed={} tookMs={}: {}",
            seedEntityId,
            System.currentTimeMillis() - started,
            validation.getMessage());
        return unavailable(
            "Could not produce a usable answer for "
                + seedEntityId
                + ". Context was assembled; try again shortly.",
            seedEntityId);
      }
      log.warn(
          "Claude ask failed for seed={} tookMs={}: {}",
          seedEntityId,
          System.currentTimeMillis() - started,
          root.toString());
      return unavailable(
          "Answer generation failed for "
              + seedEntityId
              + ". Context was assembled; try again shortly.",
          seedEntityId);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      log.warn(
          "Claude ask interrupted seed={} tookMs={}",
          seedEntityId,
          System.currentTimeMillis() - started);
      return unavailable(
          "Answer generation was interrupted for "
              + seedEntityId
              + ". Context was assembled; try again.",
          seedEntityId);
    }
  }

  private static String truncateContext(String contextBlock) {
    if (contextBlock == null) {
      return "";
    }
    if (contextBlock.length() <= MAX_CONTEXT_CHARS) {
      return contextBlock;
    }
    return contextBlock.substring(0, MAX_CONTEXT_CHARS)
        + "\n… [CONTEXT truncated for model budget]\n";
  }

  private static Throwable unwrap(Throwable ex) {
    Throwable root = ex;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root;
  }

  ClaudeAnswer parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ClaudeResponseValidationException("Claude returned an empty body");
    }
    String json = extractJsonObject(raw.trim());
    final JsonNode root;
    try {
      root = objectMapper.readTree(json);
    } catch (Exception ex) {
      throw new ClaudeResponseValidationException("Claude response was not valid JSON");
    }
    if (!root.isObject()) {
      throw new ClaudeResponseValidationException("Claude response was not a JSON object");
    }

    String answer = text(root, "answer");
    String reasoning = text(root, "reasoningSummary", "reasoning_summary");
    if (answer.isBlank() || reasoning.isBlank()) {
      throw new ClaudeResponseValidationException(
          "Claude JSON missing answer or reasoningSummary");
    }

    List<String> evidence = stringList(firstArray(root, "evidenceSources", "evidence_sources"));
    List<String> relatedFirs = stringList(firstArray(root, "relatedFirs", "related_firs"));
    List<RelatedEntity> relatedEntities =
        entityList(firstArray(root, "relatedEntities", "related_entities"));
    double confidence =
        firstNumber(root, "confidenceScore", "confidence_score").orElse(0.0);
    if (confidence < 0.0) {
      confidence = 0.0;
    }
    if (confidence > 1.0) {
      confidence = 1.0;
    }

    // Empty evidence is OK — QueryService merges CONTEXT-derived defaults.
    return new ClaudeAnswer(answer, evidence, relatedFirs, relatedEntities, confidence, reasoning);
  }

  private static ClaudeAnswer unavailable(String message, String seedEntityId) {
    return new ClaudeAnswer(
        message,
        List.of(seedEntityId),
        List.of(),
        List.of(),
        0.0,
        "Model unavailable — no unsupported conclusions drawn.");
  }

  /** Prefer a JSON object slice when Claude adds prose or markdown fences around it. */
  static String extractJsonObject(String raw) {
    String s = stripFences(raw.trim());
    int start = s.indexOf('{');
    int end = s.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return s.substring(start, end + 1).trim();
    }
    return s;
  }

  private static String stripFences(String raw) {
    String s = raw.trim();
    if (!s.startsWith("```")) {
      return s;
    }
    int firstNl = s.indexOf('\n');
    if (firstNl < 0) {
      return s;
    }
    String body = s.substring(firstNl + 1);
    int fence = body.lastIndexOf("```");
    if (fence >= 0) {
      body = body.substring(0, fence);
    }
    return body.trim();
  }

  private static String text(JsonNode root, String... fields) {
    for (String field : fields) {
      JsonNode n = root.get(field);
      if (n != null && !n.isNull()) {
        String v = n.asText("").trim();
        if (!v.isEmpty()) {
          return v;
        }
      }
    }
    return "";
  }

  private static JsonNode firstArray(JsonNode root, String... fields) {
    for (String field : fields) {
      JsonNode n = root.get(field);
      if (n != null && n.isArray()) {
        return n;
      }
    }
    return null;
  }

  private static java.util.OptionalDouble firstNumber(JsonNode root, String... fields) {
    for (String field : fields) {
      JsonNode n = root.get(field);
      if (n != null && n.isNumber()) {
        return java.util.OptionalDouble.of(n.asDouble());
      }
      if (n != null && n.isTextual()) {
        try {
          return java.util.OptionalDouble.of(Double.parseDouble(n.asText().trim()));
        } catch (NumberFormatException ignored) {
          // try next alias
        }
      }
    }
    return java.util.OptionalDouble.empty();
  }

  private static List<String> stringList(JsonNode node) {
    List<String> out = new ArrayList<>();
    if (node == null || !node.isArray()) {
      return out;
    }
    for (JsonNode item : node) {
      if (item == null || item.isNull()) {
        continue;
      }
      String v = item.isTextual() ? item.asText().trim() : item.asText("").trim();
      if (!v.isEmpty()) {
        out.add(v);
      }
    }
    return List.copyOf(out);
  }

  private static List<RelatedEntity> entityList(JsonNode node) {
    List<RelatedEntity> out = new ArrayList<>();
    if (node == null || !node.isArray()) {
      return out;
    }
    for (JsonNode item : node) {
      if (item == null || !item.isObject()) {
        continue;
      }
      String id = item.path("id").asText("").trim();
      if (id.isEmpty()) {
        continue;
      }
      out.add(
          new RelatedEntity(
              id, item.path("type").asText("").trim(), item.path("label").asText("").trim()));
    }
    return List.copyOf(out);
  }

  private static String snippet(String raw) {
    if (raw == null) {
      return "";
    }
    String oneLine = raw.replace('\n', ' ').trim();
    return oneLine.length() > 240 ? oneLine.substring(0, 240) + "…" : oneLine;
  }

  /** Which retrieval pack Claude is summarizing (mvp2/11 graph vs mvp2/12 Step F similar). */
  public enum AskTask {
    GRAPH_BRIEFING(
        "In under 120 words across 2–4 short paragraphs (separate with blank lines),"
            + " summarize what the record and immediate linked case records show about this seed"
            + " for an investigating officer. Use plain language; never say CONTEXT, PRIOR_TURNS,"
            + " Neo4j, or 1-hop. Answer only from CONTEXT (PRIOR_TURNS is continuity only)."),
    SIMILAR_CASES(
        "In under 120 words across 2–4 short paragraphs (separate with blank lines),"
            + " summarize the nearest similar FIRs listed under similarHits for an investigating"
            + " officer. Cite only FIR ids that appear in similarHits (plus the probe FIR if"
            + " needed). Rank by the given similarity scores; do not invent case links or claim"
            + " the cases are the same offender. Use plain language; never say CONTEXT,"
            + " PRIOR_TURNS, PgVector, Voyage, or embedding. Answer only from CONTEXT"
            + " (PRIOR_TURNS is continuity only). Put similar FIR ids in relatedFirs."),
    RECORDS_NL(
        "In under 120 words across 2–4 short paragraphs (separate with blank lines),"
            + " answer the officerQuestion using only the FIRs listed under similarHits."
            + " Cite only FIR ids that appear in similarHits. Rank by the given similarity scores;"
            + " do not invent case links, districts, or crime types missing from the hits."
            + " If similarHits is empty, say no matching FIRs met the similarity floor and suggest"
            + " a short modus-style phrase. Use plain language; never say CONTEXT, PRIOR_TURNS,"
            + " PgVector, Voyage, or embedding. Put cited FIR ids in relatedFirs.");

    private final String userTask;

    AskTask(String userTask) {
      this.userTask = userTask;
    }

    String userTask() {
      return userTask;
    }
  }

  public record RelatedEntity(String id, String type, String label) {}

  public record ClaudeAnswer(
      String answer,
      List<String> evidenceSources,
      List<String> relatedFirs,
      List<RelatedEntity> relatedEntities,
      double confidenceScore,
      String reasoningSummary) {}
}

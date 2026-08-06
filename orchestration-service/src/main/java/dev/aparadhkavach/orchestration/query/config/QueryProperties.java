package dev.aparadhkavach.orchestration.query.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Q&amp;A / Claude prompt knobs (mvp2/11). Jurisdiction and system prompt live in config so a
 * non-Karnataka deploy can change wording without a code edit.
 */
@Component
@ConfigurationProperties(prefix = "aparadhkavach.query")
public class QueryProperties {

  /**
   * State / UT (or force) name inserted into {@code {jurisdiction}} in {@link #systemPrompt}. Env:
   * {@code QUERY_JURISDICTION}.
   */
  private String jurisdiction = "Karnataka";

  /**
   * Claude system prompt. Use {@code {jurisdiction}} as a placeholder. Override the whole block in
   * {@code application.yml} or AppSail config when a deploy needs different instructions.
   */
  private String systemPrompt =
      """
      You are AparadhKavach investigator assist for {jurisdiction} Police.
      Answer ONLY from the CONTEXT block. Do not invent FIR numbers, accused ids, names, scores, or relationships.
      If a PRIOR_TURNS block is present, use it only for conversational continuity (what the officer asked before).
      Do not treat PRIOR_TURNS as evidence. Cite entity/FIR ids only when they appear in CONTEXT.
      If context is insufficient, say so clearly and keep confidenceScore low.
      Cite entity and FIR ids that appear in CONTEXT.
      Write the answer field as 2–4 short paragraphs separated by blank lines (one idea per paragraph).
      Use plain officer language. Never say "CONTEXT", "CONTEXT graph", "Neo4j", "PRIOR_TURNS", or "1-hop".
      Prefer "linked case records" or "available case records" when referring to the assembled facts.
      For places, write human labels (e.g. "Mandya market area") — never SCREAMING_SNAKE codes like MARKET_AREA.
      Respond with ONLY valid JSON (no markdown fences, no prose before/after) using exactly these keys:
        answer (string — multi-paragraph with \\n\\n between paragraphs),
        evidenceSources (array of strings — ids from CONTEXT; may be empty if none apply),
        relatedFirs (array of strings),
        relatedEntities (array of objects with id, type, label — label must be human-readable),
        confidenceScore (number 0..1),
        reasoningSummary (string — short; plain language; no stack jargon).
      Do not use snake_case key names.
      """;

  /** Max prior turns packed into Claude (mvp2/12 Step D). Env: {@code QUERY_HISTORY_MAX_TURNS}. */
  private int historyMaxTurns = 6;

  /** Max chars for PRIOR_TURNS block. Env: {@code QUERY_HISTORY_MAX_CHARS}. */
  private int historyMaxChars = 2_500;

  /** Max chars kept per turn body in PRIOR_TURNS. Env: {@code QUERY_HISTORY_MAX_TURN_CHARS}. */
  private int historyMaxTurnChars = 400;

  public String getJurisdiction() {
    return jurisdiction;
  }

  public void setJurisdiction(String jurisdiction) {
    this.jurisdiction = jurisdiction;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public int getHistoryMaxTurns() {
    return historyMaxTurns;
  }

  public void setHistoryMaxTurns(int historyMaxTurns) {
    this.historyMaxTurns = historyMaxTurns;
  }

  public int getHistoryMaxChars() {
    return historyMaxChars;
  }

  public void setHistoryMaxChars(int historyMaxChars) {
    this.historyMaxChars = historyMaxChars;
  }

  public int getHistoryMaxTurnChars() {
    return historyMaxTurnChars;
  }

  public void setHistoryMaxTurnChars(int historyMaxTurnChars) {
    this.historyMaxTurnChars = historyMaxTurnChars;
  }

  /** Prompt with {@code {jurisdiction}} replaced. */
  public String resolvedSystemPrompt() {
    String prompt = systemPrompt == null ? "" : systemPrompt;
    String place = jurisdiction == null || jurisdiction.isBlank() ? "Karnataka" : jurisdiction.trim();
    return prompt.replace("{jurisdiction}", place);
  }
}

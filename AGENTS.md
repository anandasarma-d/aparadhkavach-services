# AGENTS.md — aparadhkavach-services

This file governs AI-assisted code generation (Cursor, Claude Code) in this repo. It is derived from **ADR-024** (frozen governing principles) and the specific ADRs it defers to. **Apply these directly — do not re-derive, reinterpret, or re-litigate them per session.** Where a specific ADR makes a concrete decision, that ADR governs; ADR-024 governs only where no specific ADR yet applies.

Full reasoning lives in the AparadhKavach Notion workspace (Design & Schema Sections 1–14, ADRs 001–029). This file is the compiled, code-facing summary — if something here seems to conflict with what you'd otherwise infer, this file and the cited ADR win.

## Notion Access (via MCP)

**Read-only, enforced at the token level, not just by instruction.** The connected Notion integration has Read Content capability only — Insert/Update/Comment are disabled at the Notion API level. Even so: **never attempt to write, update, comment on, or create any Notion page or block, under any circumstance.** If asked to "update the Notion doc," "log this to the build log," or similar, decline and tell the person to do it via the AparadhKavach Claude Project chat instead — that is the only path by which Notion content changes, and only when Anand explicitly directs it there.

This file covers **conventions only.** For actual implementation content — the real Cypher queries, prompt templates, DDL, feature lists, RBAC rules — **fetch the current version from Notion via the connected MCP before implementing.** Do not guess, do not rely on a previous session's memory of a page, and do not assume a once-fetched copy is still current — re-fetch each session.

**Sections to fetch for this repo's work:**
- Section 5.4 — physical DataStore/Neo4j/PgVector schema (DDL, node/relationship properties)
- Section 6.5 — Cypher query library (Graph Intelligence module)
- Section 6.6 — Claude prompt templates + context block structure (Claude AI Bridge module)
- Section 6.7 — full API contract (every endpoint, request/response shape)
- Section 7.5 — QuickML feature lists (risk scorer, hotspot forecaster)
- Section 8.2 — RBAC permission matrix
- The ADR page — for any capability without an obvious existing pattern in this file

**Treat fetched Notion content as authoritative, not a starting point to improve on.** If a documented approach seems suboptimal, incomplete, or unclear, say so and ask — do not silently implement a different approach because it seems better, even if it might genuinely be better. Given the project timeline, staying consistent with what's already decided matters more than local optimization. This is the same "flag, don't silently fix" principle in §8 below, extended explicitly to Notion content.

## 1. What this system is (ADR-024 Principles 1–3)

- A **Crime Intelligence Platform**, not a chatbot. Investigation and hidden-relationship discovery, not record retrieval.
- **Neo4j and graph traversal are the primary differentiator.** Vector search (PgVector) is a supporting capability, not the core mechanism. Never default to "just do RAG" — check whether a graph traversal answers the question first.
- Every AI-generated response must be **evidence-backed and explainable** — no unsupported conclusions, always cite FIR IDs / entity IDs, always include a reasoning summary. This is enforced structurally (see §6), not just a style preference.

## 2. Repo & module structure — do not restructure

- **Maven multi-module, single repo.** Parent POM + 7 modules: `api-gateway`, `orchestration`, `investigation`, `analytics`, `auth`, `notification`, and `aparadhkavach-commons` (shared library — `ApiError`/`ErrorCode`/`ApiException` hierarchy, `GlobalExceptionHandler`, `HeaderConstants`). `commons` is a **sibling module, never a separate repo** — this was a deliberate decision (9 Jul 2026) to avoid cross-repo dependency publishing overhead. Do not suggest splitting it out.
- **7-service topology (ADR-007), not more, not fewer:**
  - `orchestration` hosts **3 in-process, real-time modules** as Java packages (not separate services): Graph Intelligence (`graph`), Claude AI Bridge (`claude`), Semantic Search (`search`) — plus a `core` package.
  - `analytics` hosts **2 in-process, batch/ML modules**: QuickML Pipeline (`quickml`), Pattern Detection (`detection`) — plus its own `core`.
  - The remaining modules (`api-gateway`, `investigation`, `auth`, `notification`) are thin, single-purpose services.
  - **Never create a new deployed service or a new Python microservice** to add a capability. Java 21 + Spring Boot + Spring AI is the default (ADR-007); a deviation requires explicit justification that Java/Spring genuinely cannot support the capability (the only accepted deviation project-wide is the separately-repo'd Sarvam STT service).
- **Package boundaries are enforced by ArchUnit, not convention** — `graph`/`claude`/`search` (orchestration) and `quickml`/`detection` (analytics) must never depend on each other; only `core` may depend into them. If you're about to import across one of these boundaries, stop — route through `core` instead. A missing or disabled ArchUnit suite is treated as a missing test, not optional (Section 13.7).

## 3. API conventions — ADR-020 (REST Design Convention)

- All endpoints are `/v1/...`, resource-oriented, standard HTTP verbs for CRUD. Non-CRUD operations use a **custom method suffix**, not a verb in the path: `POST /v1/conversations/{conversationId}/queries:voice`, `POST /v1/firs:search`, `POST /v1/pipelines/{pipelineId}:trigger`. Never invent a `/v1/doSomething` style path.
- JSON fields are **camelCase**. Table/column names are **snake_case, no `_MASTER`/`_LOG` suffix** (`firs`, `accused_persons`, `victims`, `officers`, `risk_scores`, `hotspot_forecasts`, `audit_logs`, `query_logs`, `alert_logs`, `districts`, `query_evidence`). If you see or are tempted to write `FIR_MASTER`, `ACCUSED_MASTER`, `RISK_SCORES` (uppercase), or similar — that's the pre-ADR-018 naming; it's wrong, not a valid alternative style.
- **Every list endpoint is paginated and sortable from day one** (`pageSize`/`pageToken`, `sortBy`/`sortOrder`) — ADR-020 Decision 6. This is not deferred to "if we have time." Do not scaffold a list endpoint without these params.
- **One error envelope shape, everywhere.** `ApiError`/`ErrorCode`/`ApiException` hierarchy from `aparadhkavach-commons`, handled by one `GlobalExceptionHandler`. `traceId` in every error body comes from the OTel span context (ADR-009), **never** from a client-supplied header — do not trust `X-Correlation-ID` for this field.
- Full endpoint + RBAC matrix lives in Section 8.2 (Notion, fetch fresh — do not assume 15 or 16 is still current). Any new endpoint needs a corresponding RBAC row before it ships, not after.

## 4. Auth, RBAC, district scoping (ADR-013, ADR-023, Section 8)

- 4 roles: `INVESTIGATOR`, `ANALYST`, `SUPERVISOR`, `POLICYMAKER` (+ one seeded `SUPER_ADMIN` bootstrap account only — never grant multi-role to any other account).
- JWT is **AparadhKavach's own**, issued by Auth Service after verifying a Catalyst Authentication (Embedded mode) session — not Catalyst's own token, not a third-party OAuth flow.
- District scoping is enforced by `DistrictScopeFilter`, one single enforcement point at the gateway/service boundary — do not add ad-hoc district checks elsewhere.
- **Victim identity is stripped for ANALYST and POLICYMAKER roles** — shown as `Victim [ID]`, never a name or address, in every surface (API response, Claude prompt context, UI). This is a hard rule, not a UI-only concern.
- No hardcoded magic strings for closed-set values (roles, statuses, legal codes, header names) — ADR-021. Use enums / constants classes (`HeaderConstants` in `aparadhkavach-commons`), never inline string literals for these.

## 5. AI integration boundaries (ADR-004, ADR-006, ADR-012)

- **One structured Claude call per query** — not an agentic/tool-calling loop. Agentic pattern is explicitly deferred to v2 (ADR-012). Do not build a ReAct-style loop into Claude AI Bridge.
- Claude's response must always be parsed into the 6 required fields (`answer`, `evidence_sources`, `related_firs`, `related_entities`, `confidence_score`, `reasoning_summary`) — a response missing any of these is a structured error, not a partial success.
- PgVector (`search` package) connects via **Spring AI's `PgVectorStore`, standard JDBC datasource properties** (`spring.datasource.url`/`username`/`password` + `spring.ai.vectorstore.pgvector.*`) — **not an API key.** If you're about to configure an API-key-style credential for PgVector, that's wrong; use the JDBC properties.
- Embeddings: **Voyage AI `voyage-3-large`, 1024 dimensions** (ADR-025) via a custom `EmbeddingModel` wrapper — no native Spring AI provider exists for Voyage yet, don't assume one.

## 6. Testing — non-negotiable per Section 13.7

- **ArchUnit suite is mandatory** for `orchestration` and `analytics` module boundaries (§2 above) — write it when the service is scaffolded, not deferred.
- JUnit 5 + Mockito for unit tests. **WireMock** (real embedded HTTP server) for external HTTP dependencies (Claude, Sarvam, Voyage) — not Mockito-level interface mocks, since WireMock also exercises real request/response serialization. **Testcontainers** for Neo4j/PgVector integration tests — a real instance, never mocked.
- Two independent Resilience4j circuit breaker instances: `sarvamStt` and `graphIntelligence` — don't conflate them into one, they guard different boundary types (HTTP vs. Bolt).

## 7. Observability (ADR-009, Section 9)

- OpenTelemetry SDK + OTLP export, not a proprietary APM agent or manual correlation IDs.
- Custom span naming: `{module}.{operation}` — e.g. `graph.traverse`, `claude.api_call`, `search.pgvector_search`, `quickml.feature_build`. Follow this pattern for any new span; don't invent a different naming style.
- MDC (via `RequestContextFilter` + `opentelemetry-logback-mdc-1.0`) puts `user_id`/`role`/`district`/`trace_id`/`span_id` on every log line automatically — don't hand-thread these through method signatures.

## 8. What NOT to do

- Don't add a new deployed service, a new repo, or a new Python component for something Java/Spring can do (§2).
- Don't invent REST paths outside the `/v1/` + custom-method convention (§3).
- Don't use `_MASTER`/uppercase table naming (§3) — this was already fixed project-wide once; don't reintroduce it.
- Don't build an agentic/multi-step Claude loop (§5) — that's v2, explicitly deferred.
- Don't configure PgVector with an API key (§5) — it's JDBC.
- Don't skip the ArchUnit suite or use Mockito where WireMock/Testcontainers are specified (§6).
- Don't write to Notion under any circumstance (Notion Access, above).
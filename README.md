# AparadhKavach Services

Java 21 / Spring Boot multi-module backend for **AparadhKavach** (KSP Datathon 2026 MVP-1), deployed on **Zoho Catalyst AppSail**.

**Judge checkout:** default branch `main`, or tag `mvp1-submission-2026-07-26`  
**Companion UI:** [aparadhkavach-client](https://github.com/anandasarma-d/aparadhkavach-client) → [https://aparadhkavach.onslate.in/](https://aparadhkavach.onslate.in/)

```text
Slate client
    │
    ▼
api-gateway-service  ──CORS · X-Correlation-ID · /v1/** proxy──
    ├── analytics-service      → Catalyst DataStore (risk_scores, hotspot_forecasts)
    ├── investigation-service  → DataStore + Analytics client (risk profile + case drivers)
    └── orchestration-service  → Neo4j Aura (network) · PgVector (similar cases)
```

---

## Modules (Maven)

| Module | MVP-1 role |
| --- | --- |
| `api-gateway-service` | Edge: routes, CORS, correlation ID |
| `analytics-service` | Hotspots + risk score reads from DataStore |
| `investigation-service` | Accused risk profile assembly |
| `orchestration-service` | Entity network + similar FIRs |
| `aparadhkavach-commons` | Shared DataStore/ZCQL, errors, headers |
| `auth-service` | Health stub only (JWT path = post-demo) |
| `notification-service` | Scaffold / not on demo path |

---

## Build

Requires **JDK 21** and Maven.

```bash
mvn -q -DskipTests package
# or per module, e.g.
mvn -pl api-gateway-service -am -DskipTests package
```

---

## Deploy (AppSail)

Prefer **code-only** deploys that preserve Catalyst console environment variables:

```bash
./appsail-build-deploy-health.sh --deploy --health
# optional: --only api-gateway-service
```

| Mode | When |
| --- | --- |
| `--deploy` | Routine jar deploy; strips `env_variables` from upload so console secrets stay |
| `--build-deploy` | Build + deploy with local `app-config.json` as-is, then git-restore placeholders |

**Secrets policy:** committed `*/app-config.json` files use **localhost placeholders only**. Real URLs and keys live in the **Catalyst console** (Development / Production).

Details: [docs/DEPLOY.md](docs/DEPLOY.md)

---

## MVP-1 HTTP surface (via Gateway)

| Capability | Path pattern |
| --- | --- |
| Health | `GET /health` on each AppSail |
| Risk profile | `GET /v1/accusedPersons/{id}:riskProfile` |
| Hotspots | `GET /v1/analytics/hotspots` |
| Network | `GET /v1/entities/{id}/network?depth=1\|2` |
| Similar cases | `GET /v1/firs/{firId}/similarCases` |

Every Gateway response should echo **`X-Correlation-ID`**.

---

## Related repos

| Repo | Role |
| --- | --- |
| [aparadhkavach-client](https://github.com/anandasarma-d/aparadhkavach-client) | React UI on Slate |
| [aparadhkavach-data-generator](https://github.com/anandasarma-d/aparadhkavach-data-generator) | Corpus, embeddings, QuickML CSV pipelines |

---

## More detail

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — containers, data stores, demo vs deferred  
- [docs/DEPLOY.md](docs/DEPLOY.md) — AppSail script, env policy, health checks  

---

## Notion MCP (contributors)

Copy `.cursor.mcp.json.example` → `.cursor/mcp.json` with a read-only Notion token. Never commit `.cursor/mcp.json`.

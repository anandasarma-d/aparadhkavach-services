# Services architecture (MVP-1)

## Runtime diagram

```text
┌──────────────────────── Catalyst platform ────────────────────────┐
│  Slate (React)                                                     │
│       │                                                            │
│       ▼                                                            │
│  AppSail: api-gateway-service                                      │
│       ├── AppSail: analytics-service                               │
│       ├── AppSail: investigation-service                           │
│       └── AppSail: orchestration-service                           │
│              │                                                     │
│  DataStore ◄─┴── risk_scores · hotspot_forecasts · accused_*       │
│  QuickML ──► scores imported / served via DataStore                │
└────────────────────────────────────────────────────────────────────┘
                    │                         │
                    ▼                         ▼
              Neo4j Aura                 PgVector
           (entity network)         (fir_embeddings)
```

## Responsibility split

| Service | Owns |
| --- | --- |
| **Gateway** | Path routing to downstream AppSails; CORS; `X-Correlation-ID` generate/echo/forward |
| **Analytics** | ZCQL/DataStore reads for hotspot forecasts and risk score rows |
| **Investigation** | Accused dossier + risk score join + `accused_features` case drivers |
| **Orchestration** | Neo4j neighborhood Cypher (HTTPS Query API); JDBC ANN against `fir_embeddings` |

## Explicitly not MVP-1 (designed / scaffold)

- **auth-service** JWT issue + Gateway role allowlist (Approach B / post-demo)  
- **notification-service** push/email  
- Claude conversational Q&A (F3) — Spring AI wiring may exist as future path; not the 26 Jul demo claim  

## External systems

| System | Used for |
| --- | --- |
| Catalyst DataStore | Accused, scores, forecasts, features |
| Catalyst QuickML | Offline/batch scoring → CSV → DataStore import |
| Neo4j Aura | ACCUSED_IN / ASSOCIATED_WITH / … neighborhood |
| Supabase Postgres + pgvector | Stored Voyage embeddings; similar-cases ANN |

AppSail egress: prefer **Aura HTTPS** and Supabase **IPv4 session pooler** (not IPv6-only direct DB hosts).

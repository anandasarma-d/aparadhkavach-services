# Deploy & health — AppSail

## Script

From repo root (Catalyst CLI logged in to project `aparadhkavach-dev`):

```bash
./appsail-build-deploy-health.sh --deploy --health
./appsail-build-deploy-health.sh --deploy --only orchestration-service --health
./appsail-build-deploy-health.sh --build-deploy --only analytics-service --health
```

| Flag | Behavior |
| --- | --- |
| `--deploy` | Upload jars; **omit** `env_variables` from `app-config.json` so Catalyst console env is preserved |
| `--build-deploy` | `mvn package` + deploy with local env as-is, then `git checkout` placeholders |
| `--health` | Curl `/health` on Gateway, Analytics, Investigation, Orchestration |

## Environment variables

Committed `*/app-config.json` files contain **placeholders** (`localhost`, dummy keys).

Set real values in **Catalyst Console → AppSail → Configuration → Environment Variables**, for example:

- Gateway: `INVESTIGATION_SERVICE_URL`, `ANALYTICS_SERVICE_URL`, `ORCHESTRATION_SERVICE_URL`, `CORS_ALLOWED_ORIGINS`
- Analytics / Investigation: DataStore-related Catalyst wiring as used by the commons client
- Orchestration: `NEO4J_*`, `PGVECTOR_JDBC_URL` (pooler), credentials, optional AI keys for future F3

Do **not** commit live secrets. A local cheat sheet may live in a **gitignored** file such as `appsail-console-values.local.yaml`.

## Smoke after deploy

```bash
# Replace $GW with Gateway AppSail base URL
curl -sS "$GW/health"
curl -sS "$GW/v1/accusedPersons/ACC-00040:riskProfile" -D - -o /dev/null | rg -i correlation
```

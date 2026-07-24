#!/usr/bin/env bash
# Build → deploy (Catalyst AppSail) → /health curl report for the MVP-1 Java quartet.
# Companion: aparadhkavach-docs/Auto/11-AppSail-F1-Deploy-Replay.md
#
# Usage (from this repo root):
#   ./appsail-build-deploy-health.sh              # build + deploy + health
#   ./appsail-build-deploy-health.sh --health     # health only
#   ./appsail-build-deploy-health.sh --build      # build only
#   ./appsail-build-deploy-health.sh --deploy     # deploy only (jars must exist)
#   ./appsail-build-deploy-health.sh --build --health
#
# Env overrides (optional):
#   CATALYST_PROJECT=aparadhkavach-dev
#   AN / IN / GW / ORCH   — AppSail base URLs (no trailing slash)
#
# Does NOT commit secrets. Ensure AppSail env (Neo4j / PgVector / keys) is set
# before deploy — see Auto/14 §A6 / §B4 and Auto/11 §5.
# Health report file is gitignored: appsail-health-last.txt

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

CATALYST_PROJECT="${CATALYST_PROJECT:-aparadhkavach-dev}"
REPORT="${REPORT:-$ROOT/appsail-health-last.txt}"

AN="${AN:-https://analytics-service-50044031746.development.catalystappsail.in}"
IN="${IN:-https://investigation-service-50044031746.development.catalystappsail.in}"
GW="${GW:-https://api-gateway-service-50044031746.development.catalystappsail.in}"
ORCH="${ORCH:-https://orchestration-service-50044031746.development.catalystappsail.in}"

DO_BUILD=0
DO_DEPLOY=0
DO_HEALTH=0

if [[ $# -eq 0 ]]; then
  DO_BUILD=1
  DO_DEPLOY=1
  DO_HEALTH=1
else
  for arg in "$@"; do
    case "$arg" in
      --build) DO_BUILD=1 ;;
      --deploy) DO_DEPLOY=1 ;;
      --health) DO_HEALTH=1 ;;
      -h|--help)
        sed -n '2,20p' "$0"
        exit 0
        ;;
      *)
        echo "Unknown option: $arg (try --help)" >&2
        exit 1
        ;;
    esac
  done
fi

MODULES=(
  analytics-service
  investigation-service
  api-gateway-service
  orchestration-service
)

deploy_one() {
  local module="$1"
  local jar="target/${module}.jar"
  echo "=== deploy ${module} ==="
  cd "$ROOT/$module"
  if [[ ! -f "$jar" ]]; then
    echo "Missing $module/$jar — run with --build first" >&2
    exit 1
  fi
  # Escape $ so remote AppSail expands X_ZOHO_CATALYST_LISTEN_PORT (P38/P43 pattern).
  catalyst deploy appsail \
    --project "$CATALYST_PROJECT" \
    --name "$module" \
    --build-path . \
    --stack java21 \
    --platform javase \
    --command "sh -c 'java -jar ${jar} --server.port=\$X_ZOHO_CATALYST_LISTEN_PORT'"
  cd "$ROOT"
}

if [[ "$DO_BUILD" -eq 1 ]]; then
  echo "=== mvn package (4 services) ==="
  mvn clean package -DskipTests \
    -pl analytics-service,investigation-service,api-gateway-service,orchestration-service -am
  for m in "${MODULES[@]}"; do
    ls -la "$ROOT/$m/target/${m}.jar"
  done
fi

if [[ "$DO_DEPLOY" -eq 1 ]]; then
  if ! command -v catalyst >/dev/null 2>&1; then
    echo "catalyst CLI not found on PATH" >&2
    exit 1
  fi
  for m in "${MODULES[@]}"; do
    deploy_one "$m"
  done
fi

if [[ "$DO_HEALTH" -eq 1 ]]; then
  echo "=== /health → ${REPORT} ==="
  {
    echo "# AppSail /health — $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo "# project=${CATALYST_PROJECT}"
    echo
  } >"$REPORT"

  check() {
    local name="$1"
    local base="$2"
    local url="${base}/health"
    local body_file
    body_file="$(mktemp)"
    local code
    code="$(curl -sS -o "$body_file" -w "%{http_code}" --connect-timeout 20 "$url" || echo "ERR")"
    {
      echo "## ${name}"
      echo "URL: ${url}"
      echo "HTTP: ${code}"
      echo "Body:"
      cat "$body_file"
      echo
      echo
    } >>"$REPORT"
    rm -f "$body_file"
    printf "%-16s %s  %s\n" "$name" "$code" "$url"
  }

  check analytics "$AN"
  check investigation "$IN"
  check gateway "$GW"
  check orchestration "$ORCH"

  echo
  echo "Full report: $REPORT"
  echo "Tip: if orchestration URL differs, re-run:"
  echo "  ORCH=https://<host-from-console> $0 --health"
fi

echo "Done."

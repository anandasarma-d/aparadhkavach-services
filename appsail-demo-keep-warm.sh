#!/usr/bin/env bash
#
# Keep AparadhKavach demo paths warm during rehearsal/recording.
#
# Defaults = Lane B workbench (MVP-2). Override for Lane A judge stack:
#   GW=...AN=...IN=...ORCH=... ./appsail-demo-keep-warm.sh
#
# Usage:
#   ./appsail-demo-keep-warm.sh
#   ./appsail-demo-keep-warm.sh --once
#   INTERVAL_SECONDS=120 ./appsail-demo-keep-warm.sh
#
# Feature paths (not /health alone) — D-071 / D-064: Similar cold → 408
# EXECUTION_TIME_EXCEEDED unless Orch/Gateway similarCases is warmed first.
#
# Start recording as soon as "WARM WINDOW STARTED" appears.

set -u

# Lane B (aparadhkavach-workbench / domain 50044400287)
GW="${GW:-https://api-gateway-service-50044400287.development.catalystappsail.in}"
AN="${AN:-https://analytics-service-50044400287.development.catalystappsail.in}"
IN="${IN:-https://investigation-service-50044400287.development.catalystappsail.in}"
ORCH="${ORCH:-https://orchestration-service-50044400287.development.catalystappsail.in}"

# Demo IDs locked for Lane B rehearsal (Slate screenshots 28 Jul)
NET_ACCUSED="${NET_ACCUSED:-ACC-00044}"
RISK_A="${RISK_A:-ACC-00040}"
RISK_B="${RISK_B:-ACC-00124}"
SIM_A="${SIM_A:-FIR-003276}"
SIM_B="${SIM_B:-FIR-002683}"

INTERVAL_SECONDS="${INTERVAL_SECONDS:-120}"
CONNECT_TIMEOUT_SECONDS="${CONNECT_TIMEOUT_SECONDS:-15}"
# Similar cold path can need >40s before AppSail 408 — allow headroom for warm attempts
MAX_TIME_SECONDS="${MAX_TIME_SECONDS:-90}"

MODE="loop"
case "${1:-}" in
  "") ;;
  --once) MODE="once" ;;
  -h|--help)
    awk 'NR >= 2 && NR <= 16 { sub(/^# ?/, ""); print }' "$0"
    exit 0
    ;;
  *)
    echo "Unknown argument: $1 (expected --once or --help)" >&2
    exit 2
    ;;
esac

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required but was not found on PATH" >&2
  exit 1
fi

if ! [[ "$INTERVAL_SECONDS" =~ ^[0-9]+$ ]] || (( INTERVAL_SECONDS < 30 )); then
  echo "INTERVAL_SECONDS must be an integer of at least 30" >&2
  exit 2
fi

request() {
  local label="$1"
  local url="$2"
  local result
  local curl_exit=0

  result="$(
    curl \
      --silent \
      --show-error \
      --output /dev/null \
      --connect-timeout "$CONNECT_TIMEOUT_SECONDS" \
      --max-time "$MAX_TIME_SECONDS" \
      --write-out "%{http_code}|%{time_total}" \
      "$url"
  )" || curl_exit=$?

  if (( curl_exit == 0 )); then
    local code="${result%%|*}"
    local elapsed="${result#*|}"
    printf "  %-28s HTTP %-3s  %7ss\n" "$label" "$code" "$elapsed"
    [[ "$code" =~ ^2 ]]
  else
    printf "  %-28s ERROR curl=%s\n" "$label" "$curl_exit"
    return 1
  fi
}

# AppSail often 408s the *first* Gateway→Orch call even after Orch direct is warm
# (Gateway or second instance cold). Retry until 2xx or attempts exhausted.
request_retry() {
  local label="$1"
  local url="$2"
  local attempts="${3:-4}"
  local i
  for ((i = 1; i <= attempts; i++)); do
    if request "${label} (try ${i}/${attempts})" "$url"; then
      return 0
    fi
    if ((i < attempts)); then
      sleep 2
    fi
  done
  return 1
}

sweep=0
while :; do
  sweep=$((sweep + 1))
  started_at="$(date '+%Y-%m-%d %H:%M:%S')"
  failures=0

  echo
  echo "=== AparadhKavach demo warm-up #${sweep} — ${started_at} ==="
  echo "Targets: GW=${GW}"
  echo "Direct service health:"
  request "Gateway health"       "$GW/health"   || failures=$((failures + 1))
  request "Analytics health"     "$AN/health"   || failures=$((failures + 1))
  request "Investigation health" "$IN/health"   || failures=$((failures + 1))
  request "Orchestration health" "$ORCH/health" || failures=$((failures + 1))

  echo "Feature warm (Orch first — Neo4j Network + PgVector Similar; D-071/D-063/D-064):"
  request "Orch Network d1 ${NET_ACCUSED}" \
    "$ORCH/v1/entities/${NET_ACCUSED}/network?depth=1" || failures=$((failures + 1))
  request "Orch Network d2 ${NET_ACCUSED}" \
    "$ORCH/v1/entities/${NET_ACCUSED}/network?depth=2" || failures=$((failures + 1))
  request "Orch Similar ${SIM_A}" \
    "$ORCH/v1/firs/${SIM_A}/similarCases?limit=5" || failures=$((failures + 1))
  request "Orch Similar ${SIM_B}" \
    "$ORCH/v1/firs/${SIM_B}/similarCases?limit=5" || failures=$((failures + 1))
  # Direct Investigation riskProfile — Orch ask soft-fails in 3s if this is cold
  request "Investigation risk ${RISK_A}" \
    "$IN/v1/accusedPersons/${RISK_A}:riskProfile" || failures=$((failures + 1))

  echo "Demo features through Gateway (retry on 408 — D-063/D-064):"
  request_retry "Risk ${RISK_A}" \
    "$GW/v1/accusedPersons/${RISK_A}:riskProfile" || failures=$((failures + 1))
  request "Risk ${RISK_B}" \
    "$GW/v1/accusedPersons/${RISK_B}:riskProfile" || failures=$((failures + 1))
  request "Hotspots" \
    "$GW/v1/analytics/hotspots?limit=20" || failures=$((failures + 1))
  request_retry "Network depth 1 ${NET_ACCUSED}" \
    "$GW/v1/entities/${NET_ACCUSED}/network?depth=1" || failures=$((failures + 1))
  request_retry "Network depth 2 ${NET_ACCUSED}" \
    "$GW/v1/entities/${NET_ACCUSED}/network?depth=2" || failures=$((failures + 1))
  request_retry "Similar ${SIM_A}" \
    "$GW/v1/firs/${SIM_A}/similarCases?limit=5" || failures=$((failures + 1))
  request_retry "Similar ${SIM_B}" \
    "$GW/v1/firs/${SIM_B}/similarCases?limit=5" || failures=$((failures + 1))

  echo
  if (( failures == 0 )); then
    echo "WARM WINDOW STARTED — all endpoints returned 2xx. Start recording now."
  else
    echo "WARM WINDOW STARTED — ${failures} request(s) failed; review timings above."
  fi

  if [[ "$MODE" == "once" ]]; then
    exit "$(( failures == 0 ? 0 : 1 ))"
  fi

  echo "Next sweep starts in ${INTERVAL_SECONDS}s. Press Ctrl-C to stop."
  sleep "$INTERVAL_SECONDS"
done

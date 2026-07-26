#!/usr/bin/env bash
#
# Keep the AparadhKavach MVP-1 demo path warm during rehearsal/recording.
#
# Usage:
#   ./appsail-demo-keep-warm.sh
#   ./appsail-demo-keep-warm.sh --once
#   INTERVAL_SECONDS=180 ./appsail-demo-keep-warm.sh
#
# The default loop performs a full sweep, then waits three minutes before
# starting the next one. Start recording as soon as "WARM WINDOW STARTED"
# appears so feature requests do not overlap the recording.

set -u

GW="${GW:-https://api-gateway-service-50044031746.development.catalystappsail.in}"
AN="${AN:-https://analytics-service-50044031746.development.catalystappsail.in}"
IN="${IN:-https://investigation-service-50044031746.development.catalystappsail.in}"
ORCH="${ORCH:-https://orchestration-service-50044031746.development.catalystappsail.in}"

INTERVAL_SECONDS="${INTERVAL_SECONDS:-180}"
CONNECT_TIMEOUT_SECONDS="${CONNECT_TIMEOUT_SECONDS:-10}"
MAX_TIME_SECONDS="${MAX_TIME_SECONDS:-40}"

MODE="loop"
case "${1:-}" in
  "") ;;
  --once) MODE="once" ;;
  -h|--help)
    awk 'NR >= 2 && NR <= 11 { sub(/^# ?/, ""); print }' "$0"
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
    printf "  %-26s HTTP %-3s  %7ss\n" "$label" "$code" "$elapsed"
    [[ "$code" =~ ^2 ]]
  else
    printf "  %-26s ERROR curl=%s\n" "$label" "$curl_exit"
    return 1
  fi
}

sweep=0
while :; do
  sweep=$((sweep + 1))
  started_at="$(date '+%Y-%m-%d %H:%M:%S')"
  failures=0

  echo
  echo "=== AparadhKavach demo warm-up #${sweep} — ${started_at} ==="
  echo "Direct service health:"
  request "Gateway health"       "$GW/health"   || failures=$((failures + 1))
  request "Analytics health"     "$AN/health"   || failures=$((failures + 1))
  request "Investigation health" "$IN/health"   || failures=$((failures + 1))
  request "Orchestration health" "$ORCH/health" || failures=$((failures + 1))

  echo "Demo features through Gateway:"
  request "Risk ACC-00040" \
    "$GW/v1/accusedPersons/ACC-00040:riskProfile" || failures=$((failures + 1))
  request "Risk ACC-00046" \
    "$GW/v1/accusedPersons/ACC-00046:riskProfile" || failures=$((failures + 1))
  request "Hotspots" \
    "$GW/v1/analytics/hotspots" || failures=$((failures + 1))
  request "Network depth 1" \
    "$GW/v1/entities/ACC-00040/network?depth=1" || failures=$((failures + 1))
  request "Network depth 2" \
    "$GW/v1/entities/ACC-00040/network?depth=2" || failures=$((failures + 1))
  request "Similar FIR-002683" \
    "$GW/v1/firs/FIR-002683/similarCases?limit=5" || failures=$((failures + 1))
  request "Similar FIR-003276" \
    "$GW/v1/firs/FIR-003276/similarCases?limit=5" || failures=$((failures + 1))

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

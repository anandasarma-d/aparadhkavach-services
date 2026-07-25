#!/usr/bin/env bash
# Build / deploy / health for the MVP-1 Java AppSail quartet.
# Companion: aparadhkavach-docs/Auto/11-AppSail-F1-Deploy-Replay.md
#
# Usage (from this repo root):
#   ./appsail-build-deploy-health.sh --build-deploy [--only MODULE] [--health]
#       Build jars, deploy with each module's app-config.json AS-IS (pushes
#       env_variables to Catalyst), then git-checkout app-config.json back to
#       HEAD (placeholders). Use when you intentionally staged real console
#       values in the local files for a one-shot recreate.
#
#   ./appsail-build-deploy-health.sh --deploy [--only MODULE] [--health]
#       Deploy existing jars WITHOUT touching Catalyst console env vars.
#       Temporarily strips env_variables from app-config.json for the upload,
#       then restores the local file. Prefer this for routine code deploys.
#
#   ./appsail-build-deploy-health.sh --build [--only MODULE]
#   ./appsail-build-deploy-health.sh --health
#
# MODULE names: analytics-service | investigation-service |
#               api-gateway-service | orchestration-service
#
# Env overrides (optional):
#   CATALYST_PROJECT=aparadhkavach-dev
#   AN / IN / GW / ORCH   — AppSail base URLs (no trailing slash)
#
# Notes:
#   - Catalyst CLI has no "skip env" flag; --deploy strips env_variables for
#     the upload only. Startup command / stack / memory still come from the file.
#   - Do NOT commit secrets. Real values live in the Catalyst console (Auto/11 §0a)
#     or temporarily in local app-config for --build-deploy, then get reverted.
#   - Health report is gitignored: appsail-health-last.txt
#
# Default with no args prints this help and exits (avoids accidental overwrite
# of console env with localhost placeholders).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

CATALYST_PROJECT="${CATALYST_PROJECT:-aparadhkavach-dev}"
REPORT="${REPORT:-$ROOT/appsail-health-last.txt}"

AN="${AN:-https://analytics-service-50044031746.development.catalystappsail.in}"
IN="${IN:-https://investigation-service-50044031746.development.catalystappsail.in}"
GW="${GW:-https://api-gateway-service-50044031746.development.catalystappsail.in}"
ORCH="${ORCH:-https://orchestration-service-50044031746.development.catalystappsail.in}"

ALL_MODULES=(
  analytics-service
  investigation-service
  api-gateway-service
  orchestration-service
)

DO_BUILD=0
DO_DEPLOY=0
DO_HEALTH=0
DEPLOY_MODE=""   # "as-is" | "code-only"
ONLY_MODULE=""

usage() {
  sed -n '2,36p' "$0"
}

if [[ $# -eq 0 ]]; then
  usage
  exit 1
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build)
      DO_BUILD=1
      shift
      ;;
    --build-deploy)
      DO_BUILD=1
      DO_DEPLOY=1
      DEPLOY_MODE="as-is"
      shift
      ;;
    --deploy)
      DO_DEPLOY=1
      # Prefer code-only unless --build-deploy already set this run
      if [[ -z "$DEPLOY_MODE" ]]; then
        DEPLOY_MODE="code-only"
      fi
      shift
      ;;
    --health)
      DO_HEALTH=1
      shift
      ;;
    --only)
      if [[ $# -lt 2 ]]; then
        echo "--only requires a module name" >&2
        exit 1
      fi
      ONLY_MODULE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1 (try --help)" >&2
      exit 1
      ;;
  esac
done

if [[ "$DO_DEPLOY" -eq 1 && -z "$DEPLOY_MODE" ]]; then
  echo "Internal error: deploy requested without mode" >&2
  exit 1
fi

MODULES=()
if [[ -n "$ONLY_MODULE" ]]; then
  found=0
  for m in "${ALL_MODULES[@]}"; do
    if [[ "$m" == "$ONLY_MODULE" ]]; then
      found=1
      break
    fi
  done
  if [[ "$found" -ne 1 ]]; then
    echo "Unknown module: $ONLY_MODULE" >&2
    echo "Expected one of: ${ALL_MODULES[*]}" >&2
    exit 1
  fi
  MODULES=("$ONLY_MODULE")
else
  MODULES=("${ALL_MODULES[@]}")
fi

modules_csv() {
  local IFS=,
  echo "${MODULES[*]}"
}

# Write app-config without env_variables (preserve console on Catalyst).
# Backup path returned via stdout.
strip_env_variables() {
  local cfg="$1"
  local bak
  bak="$(mktemp "${cfg}.XXXXXX.bak")"
  cp "$cfg" "$bak"
  python3 - "$cfg" <<'PY'
import json, sys
path = sys.argv[1]
with open(path) as f:
    data = json.load(f)
data.pop("env_variables", None)
with open(path, "w") as f:
    json.dump(data, f, indent=2)
    f.write("\n")
PY
  echo "$bak"
}

restore_file() {
  local cfg="$1"
  local bak="$2"
  if [[ -f "$bak" ]]; then
    mv "$bak" "$cfg"
  fi
}

revert_app_config_to_git() {
  local module="$1"
  local cfg="$ROOT/$module/app-config.json"
  if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git -C "$ROOT" checkout -- "$module/app-config.json" 2>/dev/null || true
    echo "  reverted $module/app-config.json → git HEAD"
  else
    echo "  WARN: not a git repo — left $cfg as deployed" >&2
  fi
}

deploy_one() {
  local module="$1"
  local mode="$2"
  local jar="target/${module}.jar"
  local cfg="$ROOT/$module/app-config.json"
  local bak=""

  echo "=== deploy ${module} (mode=${mode}) ==="
  cd "$ROOT/$module"
  if [[ ! -f "$jar" ]]; then
    echo "Missing $module/$jar — run with --build or --build-deploy first" >&2
    exit 1
  fi
  if [[ ! -f "$cfg" ]]; then
    echo "Missing $cfg" >&2
    exit 1
  fi

  if [[ "$mode" == "code-only" ]]; then
    echo "  stripping env_variables for upload (console env preserved)"
    bak="$(strip_env_variables "$cfg")"
  else
    echo "  using app-config.json as-is (will push env_variables)"
  fi

  # Escape $ so remote AppSail expands X_ZOHO_CATALYST_LISTEN_PORT (P38/P43).
  set +e
  catalyst deploy appsail \
    --project "$CATALYST_PROJECT" \
    --name "$module" \
    --build-path . \
    --stack java21 \
    --platform javase \
    --command "sh -c 'java -jar ${jar} --server.port=\$X_ZOHO_CATALYST_LISTEN_PORT'"
  local rc=$?
  set -e

  if [[ "$mode" == "code-only" && -n "$bak" ]]; then
    restore_file "$cfg" "$bak"
    echo "  restored local app-config.json"
  elif [[ "$mode" == "as-is" ]]; then
    revert_app_config_to_git "$module"
  fi

  cd "$ROOT"
  if [[ "$rc" -ne 0 ]]; then
    echo "Deploy failed for $module (exit $rc)" >&2
    exit "$rc"
  fi
}

if [[ "$DO_BUILD" -eq 1 ]]; then
  echo "=== mvn package ($(modules_csv)) ==="
  mvn clean package -DskipTests -pl "$(modules_csv)" -am
  for m in "${MODULES[@]}"; do
    ls -la "$ROOT/$m/target/${m}.jar"
  done
fi

if [[ "$DO_DEPLOY" -eq 1 ]]; then
  if ! command -v catalyst >/dev/null 2>&1; then
    echo "catalyst CLI not found on PATH" >&2
    exit 1
  fi
  if [[ "$DEPLOY_MODE" == "as-is" ]]; then
    echo
    echo "WARNING: --build-deploy pushes env_variables from local app-config.json."
    echo "         Ensure those files hold the REAL values you want on Catalyst,"
    echo "         then they will be git-checkout'd back to HEAD after each deploy."
    echo
  fi
  for m in "${MODULES[@]}"; do
    deploy_one "$m" "$DEPLOY_MODE"
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

  # Only check services we touched when --only was used; else all four.
  if [[ -n "$ONLY_MODULE" ]]; then
    case "$ONLY_MODULE" in
      analytics-service) check analytics "$AN" ;;
      investigation-service) check investigation "$IN" ;;
      api-gateway-service) check gateway "$GW" ;;
      orchestration-service) check orchestration "$ORCH" ;;
    esac
  else
    check analytics "$AN"
    check investigation "$IN"
    check gateway "$GW"
    check orchestration "$ORCH"
  fi

  echo
  echo "Full report: $REPORT"
  echo "Tip: if a URL differs, override e.g.:"
  echo "  ORCH=https://<host-from-console> $0 --health"
fi

echo "Done."

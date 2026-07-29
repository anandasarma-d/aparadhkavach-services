#!/usr/bin/env bash
# Build / deploy / health for the MVP-1 Java AppSail quartet.
# Companion: aparadhkavach-docs/Auto/11-AppSail-F1-Deploy-Replay.md
# Phase 2 / Lane B: aparadhkavach-docs/Auto/mvp2/06-AppSail-Deploy-Replay.md
#
# Usage (from this repo root):
#   ./appsail-build-deploy-health.sh --project aparadhkavach-workbench --deploy [--only MODULE] [--health]
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
# Project targeting (deploy always uses --project; do not rely on project:use alone):
#   --project NAME              preferred (e.g. aparadhkavach-workbench)
#   CATALYST_PROJECT=NAME       env equivalent if --project omitted
#   default                     aparadhkavach-dev (Lane A / judge)
#
# Health URL defaults are derived from .catalystrc domain id for --project when
# AN / IN / GW / ORCH are unset. Override explicitly if a URL differs.
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

# Preserve caller URL overrides (may be empty).
_AN_OVERRIDE="${AN-}"
_IN_OVERRIDE="${IN-}"
_GW_OVERRIDE="${GW-}"
_ORCH_OVERRIDE="${ORCH-}"
_AUTH_OVERRIDE="${AUTH-}"
_PROJECT_FROM_ENV="${CATALYST_PROJECT-}"

REPORT="${REPORT:-$ROOT/appsail-health-last.txt}"

ALL_MODULES=(
  analytics-service
  investigation-service
  api-gateway-service
  orchestration-service
  auth-service
)

DO_BUILD=0
DO_DEPLOY=0
DO_HEALTH=0
DEPLOY_MODE=""   # "as-is" | "code-only"
ONLY_MODULE=""
PROJECT_ARG=""

usage() {
  sed -n '2,45p' "$0"
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
    --project)
      if [[ $# -lt 2 ]]; then
        echo "--project requires a Catalyst project name" >&2
        exit 1
      fi
      PROJECT_ARG="$2"
      shift 2
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

# --project wins over env; env wins over Lane A default.
CATALYST_PROJECT="${PROJECT_ARG:-${_PROJECT_FROM_ENV:-aparadhkavach-dev}}"

domain_id_for_project() {
  local name="$1"
  if [[ ! -f "$ROOT/.catalystrc" ]] || ! command -v python3 >/dev/null 2>&1; then
    return 1
  fi
  python3 - "$ROOT/.catalystrc" "$name" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
want = sys.argv[2]
for p in data.get("projects", []):
    if p.get("name") == want:
        dom = (p.get("domain") or {}).get("id") or ""
        if dom:
            print(dom)
            sys.exit(0)
sys.exit(1)
PY
}

active_project_name() {
  if [[ ! -f "$ROOT/.catalystrc" ]] || ! command -v python3 >/dev/null 2>&1; then
    return 1
  fi
  python3 - "$ROOT/.catalystrc" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
active = data.get("actives", {}).get("project")
for p in data.get("projects", []):
    if p.get("idx") == active:
        print(p.get("name", ""))
        break
PY
}

DOMAIN_ID="$(domain_id_for_project "$CATALYST_PROJECT" || true)"
if [[ -z "$DOMAIN_ID" ]]; then
  # Fallback known domains if .catalystrc incomplete
  case "$CATALYST_PROJECT" in
    aparadhkavach-dev) DOMAIN_ID="50044031746" ;;
    aparadhkavach-workbench) DOMAIN_ID="50044400287" ;;
    *)
      echo "WARNING: unknown domain id for project '${CATALYST_PROJECT}'." >&2
      echo "         Set AN/IN/GW/ORCH explicitly for --health." >&2
      DOMAIN_ID="50044031746"
      ;;
  esac
fi

AN="${_AN_OVERRIDE:-https://analytics-service-${DOMAIN_ID}.development.catalystappsail.in}"
IN="${_IN_OVERRIDE:-https://investigation-service-${DOMAIN_ID}.development.catalystappsail.in}"
GW="${_GW_OVERRIDE:-https://api-gateway-service-${DOMAIN_ID}.development.catalystappsail.in}"
ORCH="${_ORCH_OVERRIDE:-https://orchestration-service-${DOMAIN_ID}.development.catalystappsail.in}"
AUTH="${_AUTH_OVERRIDE:-https://auth-service-${DOMAIN_ID}.development.catalystappsail.in}"

_active_name="$(active_project_name || true)"
if [[ -n "$_active_name" && "$_active_name" != "$CATALYST_PROJECT" ]]; then
  echo "WARNING: .catalystrc active project is '${_active_name}' but deploy target is '${CATALYST_PROJECT}'." >&2
  echo "         Deploy uses --project \"${CATALYST_PROJECT}\" (from --project / CATALYST_PROJECT / default)." >&2
fi
echo "Using Catalyst project: ${CATALYST_PROJECT} (domain ${DOMAIN_ID})"
unset _AN_OVERRIDE _IN_OVERRIDE _GW_OVERRIDE _ORCH_OVERRIDE _AUTH_OVERRIDE _PROJECT_FROM_ENV _active_name

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

  # Only check services we touched when --only was used; else all five.
  if [[ -n "$ONLY_MODULE" ]]; then
    case "$ONLY_MODULE" in
      analytics-service) check analytics "$AN" ;;
      investigation-service) check investigation "$IN" ;;
      api-gateway-service) check gateway "$GW" ;;
      orchestration-service) check orchestration "$ORCH" ;;
      auth-service) check auth "$AUTH" ;;
    esac
  else
    check analytics "$AN"
    check investigation "$IN"
    check gateway "$GW"
    check orchestration "$ORCH"
    check auth "$AUTH"
  fi

  echo
  echo "Full report: $REPORT"
  echo "Tip: if a URL differs, override e.g.:"
  echo "  ORCH=https://<host-from-console> $0 --health"
fi

echo "Done."

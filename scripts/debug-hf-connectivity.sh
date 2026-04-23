#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${CVECT_ENV_FILE:-${ROOT_DIR}/.env.embedding}"
COMPOSE_FILE="${CVECT_COMPOSE_FILE:-${ROOT_DIR}/docker-compose.embedding.yml}"

read_env_value() {
  local key="$1"
  local line
  local value=""

  [[ -f "${ENV_FILE}" ]] || return 0

  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ "${line}" =~ ^[[:space:]]*# ]] && continue
    [[ "${line}" =~ ^[[:space:]]*$ ]] && continue

    case "${line}" in
      "${key}"=*)
        value="${line#*=}"
        value="${value%$'\r'}"
        if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
          value="${value:1:-1}"
        elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
          value="${value:1:-1}"
        fi
        printf '%s' "${value}"
        return 0
        ;;
    esac
  done < "${ENV_FILE}"
}

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Create or restore it first." >&2
  exit 1
fi

HF_ENDPOINT="${CVECT_HF_ENDPOINT:-$(read_env_value CVECT_HF_ENDPOINT)}"
EMBEDDING_MODEL="${CVECT_EMBEDDING_MODEL:-$(read_env_value CVECT_EMBEDDING_MODEL)}"
HF_CACHE_DIR="${CVECT_HF_CACHE_DIR:-$(read_env_value CVECT_HF_CACHE_DIR)}"
HTTP_PROXY_VALUE="${CVECT_HTTP_PROXY:-$(read_env_value CVECT_HTTP_PROXY)}"
HTTPS_PROXY_VALUE="${CVECT_HTTPS_PROXY:-$(read_env_value CVECT_HTTPS_PROXY)}"
NO_PROXY_VALUE="${CVECT_NO_PROXY:-$(read_env_value CVECT_NO_PROXY)}"
HF_HUB_OFFLINE="${CVECT_HF_HUB_OFFLINE:-$(read_env_value CVECT_HF_HUB_OFFLINE)}"
HF_LOCAL_FILES_ONLY="${CVECT_HF_LOCAL_FILES_ONLY:-$(read_env_value CVECT_HF_LOCAL_FILES_ONLY)}"
HF_HUB_DISABLE_XET="${CVECT_HF_HUB_DISABLE_XET:-$(read_env_value CVECT_HF_HUB_DISABLE_XET)}"

export HTTP_PROXY="${HTTP_PROXY_VALUE:-}"
export HTTPS_PROXY="${HTTPS_PROXY_VALUE:-}"
export NO_PROXY="${NO_PROXY_VALUE:-}"
export http_proxy="${HTTP_PROXY_VALUE:-}"
export https_proxy="${HTTPS_PROXY_VALUE:-}"
export no_proxy="${NO_PROXY_VALUE:-}"

resolve_url() {
  local model_id="$1"
  printf '%s/%s/resolve/main/config.json' "${HF_ENDPOINT%/}" "${model_id}"
}

HOST_PROBE_URL="$(resolve_url "${EMBEDDING_MODEL}")"

echo "HF endpoint:            ${HF_ENDPOINT}"
echo "Embedding model probe:  ${HOST_PROBE_URL}"
echo "HF cache dir:           ${HF_CACHE_DIR:-<empty>}"
echo "HF_HUB_OFFLINE:         ${HF_HUB_OFFLINE:-}"
echo "HF_LOCAL_FILES_ONLY:    ${HF_LOCAL_FILES_ONLY:-}"
echo "HF_HUB_DISABLE_XET:     ${HF_HUB_DISABLE_XET:-}"
echo "HTTP_PROXY:             ${HTTP_PROXY_VALUE:-<empty>}"
echo "HTTPS_PROXY:            ${HTTPS_PROXY_VALUE:-<empty>}"
echo "NO_PROXY:               ${NO_PROXY_VALUE:-<empty>}"
echo

echo "[1/4] Host curl probe"
curl -I --max-time 15 --location "${HOST_PROBE_URL}" || true
echo

echo "[2/4] Host DNS probe"
python3 - <<'PY' "${HF_ENDPOINT}"
import socket
import sys
import urllib.parse

host = urllib.parse.urlparse(sys.argv[1]).hostname
print(f"hostname={host}")
if not host:
    raise SystemExit("unable to parse host from HF endpoint")
for row in socket.getaddrinfo(host, 443, proto=socket.IPPROTO_TCP):
    print(row[4][0])
PY
echo

echo "[3/4] Docker compose qwen status"
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps qwen || true
echo

if [[ -n "${HF_CACHE_DIR:-}" ]]; then
  CACHE_PATH="${HF_CACHE_DIR}"
  if [[ "${CACHE_PATH}" != /* ]]; then
    CACHE_PATH="${ROOT_DIR}/${CACHE_PATH#./}"
  fi
  echo "Local cache contents"
  if [[ -d "${CACHE_PATH}/hub" ]]; then
    find "${CACHE_PATH}/hub" -mindepth 1 -maxdepth 1 -type d -name 'models--*' | sort || true
  else
    echo "(cache directory missing or empty)"
  fi
  echo
fi

QWEN_CID="$(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps -q qwen 2>/dev/null || true)"
if [[ -n "${QWEN_CID}" ]]; then
  echo "[4/4] In-container probe"
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" exec -T qwen python - <<'PY' "${HOST_PROBE_URL}"
import os
import sys
import urllib.request

url = sys.argv[1]
for key in [
    "HF_ENDPOINT",
    "HF_HUB_OFFLINE",
    "HF_LOCAL_FILES_ONLY",
    "HF_HUB_DISABLE_XET",
    "HTTP_PROXY",
    "HTTPS_PROXY",
    "NO_PROXY",
]:
    print(f"{key}={os.getenv(key, '')}")
print(f"probing={url}")
with urllib.request.urlopen(url, timeout=15) as resp:
    print(f"status={resp.status}")
    print(f"final_url={resp.geturl()}")
PY
else
  echo "[4/4] Skip in-container probe: qwen container is not created yet."
fi
echo

echo "Recent qwen logs"
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" logs --tail=80 qwen || true

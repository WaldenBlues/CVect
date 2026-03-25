#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"

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

HTTP_PORT="${CVECT_HTTP_PORT:-}"
if [[ -z "${HTTP_PORT}" ]]; then
  HTTP_PORT="$(read_env_value CVECT_HTTP_PORT)"
fi
if [[ -z "${HTTP_PORT}" ]]; then
  HTTP_PORT="8088"
fi

BASIC_AUTH_USERNAME="${CVECT_BASIC_AUTH_USERNAME:-}"
if [[ -z "${BASIC_AUTH_USERNAME}" ]]; then
  BASIC_AUTH_USERNAME="$(read_env_value CVECT_BASIC_AUTH_USERNAME)"
fi

BASIC_AUTH_PASSWORD="${CVECT_BASIC_AUTH_PASSWORD:-}"
if [[ -z "${BASIC_AUTH_PASSWORD}" ]]; then
  BASIC_AUTH_PASSWORD="$(read_env_value CVECT_BASIC_AUTH_PASSWORD)"
fi

DEFAULT_PORT="${HTTP_PORT}"
BASE_URL="${1:-http://127.0.0.1:${DEFAULT_PORT}}"
CURL_ARGS=(-fsS)

if [[ -n "${BASIC_AUTH_USERNAME:-}" ]]; then
  CURL_ARGS+=(-u "${BASIC_AUTH_USERNAME}:${BASIC_AUTH_PASSWORD:-}")
fi

assert_json_status_up() {
  local payload="$1"
  if ! grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"${payload}"; then
    echo "Unexpected health payload: ${payload}" >&2
    exit 1
  fi
}

echo "Checking ${BASE_URL}"
curl "${CURL_ARGS[@]}" "${BASE_URL}/healthz" >/dev/null
curl "${CURL_ARGS[@]}" "${BASE_URL}/api/resumes/health"
echo
vector_health="$(curl "${CURL_ARGS[@]}" "${BASE_URL}/api/vector/health")"
printf '%s\n' "${vector_health}"
assert_json_status_up "${vector_health}"
echo
echo "Smoke test passed"

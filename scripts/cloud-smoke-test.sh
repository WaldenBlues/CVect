#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://127.0.0.1}"
CURL_ARGS=(-fsS)

if [[ -n "${CVECT_BASIC_AUTH_USERNAME:-}" ]]; then
  CURL_ARGS+=(-u "${CVECT_BASIC_AUTH_USERNAME}:${CVECT_BASIC_AUTH_PASSWORD:-}")
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

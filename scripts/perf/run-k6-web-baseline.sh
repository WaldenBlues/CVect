#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RESULT_DIR="${ROOT_DIR}/tmp/perf"
SCRIPT_PATH="/work/scripts/perf/k6-web-baseline.js"

mkdir -p "${RESULT_DIR}"

: "${BASE_URL:=http://127.0.0.1:8088}"
: "${BASIC_USER:=}"
: "${BASIC_PASSWORD:=}"
: "${K6_IMAGE:=grafana/k6:0.49.0}"
: "${K6_NETWORK:=host}"
: "${SUMMARY_NAME:=k6-web-baseline-summary.json}"
: "${SETUP_TIMEOUT:=10m}"

if [[ -n "${BASIC_USER}" && -z "${BASIC_PASSWORD}" ]] || [[ -z "${BASIC_USER}" && -n "${BASIC_PASSWORD}" ]]; then
  echo "BASIC_USER and BASIC_PASSWORD must both be set or both be empty." >&2
  exit 1
fi

docker run --rm --network "${K6_NETWORK}" \
  -u "$(id -u):$(id -g)" \
  -e BASE_URL="${BASE_URL}" \
  -e BASIC_USER="${BASIC_USER}" \
  -e BASIC_PASSWORD="${BASIC_PASSWORD}" \
  -e SETUP_TIMEOUT="${SETUP_TIMEOUT}" \
  -e JDS_VUS="${JDS_VUS:-5}" \
  -e JD_DETAIL_VUS="${JD_DETAIL_VUS:-3}" \
  -e CANDIDATES_VUS="${CANDIDATES_VUS:-5}" \
  -e CANDIDATE_DETAIL_VUS="${CANDIDATE_DETAIL_VUS:-3}" \
  -e AUDIT_LOG_VUS="${AUDIT_LOG_VUS:-3}" \
  -e RESUME_HEALTH_VUS="${RESUME_HEALTH_VUS:-3}" \
  -e SEARCH_MISS_VUS="${SEARCH_MISS_VUS:-3}" \
  -e SEARCH_HIT_VUS="${SEARCH_HIT_VUS:-3}" \
  -e JDS_DURATION="${JDS_DURATION:-30s}" \
  -e JD_DETAIL_DURATION="${JD_DETAIL_DURATION:-30s}" \
  -e CANDIDATES_DURATION="${CANDIDATES_DURATION:-30s}" \
  -e CANDIDATE_DETAIL_DURATION="${CANDIDATE_DETAIL_DURATION:-30s}" \
  -e AUDIT_LOG_DURATION="${AUDIT_LOG_DURATION:-30s}" \
  -e RESUME_HEALTH_DURATION="${RESUME_HEALTH_DURATION:-30s}" \
  -e SEARCH_MISS_DURATION="${SEARCH_MISS_DURATION:-30s}" \
  -e SEARCH_HIT_DURATION="${SEARCH_HIT_DURATION:-30s}" \
  -v "${ROOT_DIR}:/work" \
  -v "${RESULT_DIR}:/results" \
  -w /work \
  "${K6_IMAGE}" run \
  --summary-export "/results/${SUMMARY_NAME}" \
  "${SCRIPT_PATH}"

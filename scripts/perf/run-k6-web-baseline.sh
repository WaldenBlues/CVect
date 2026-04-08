#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RESULT_DIR="${ROOT_DIR}/tmp/perf"
SCRIPT_PATH="/work/scripts/perf/k6-web-baseline.js"

mkdir -p "${RESULT_DIR}"

: "${BASE_URL:=http://127.0.0.1:8088}"
: "${BASIC_USER:=demo}"
: "${BASIC_PASSWORD:=demo123}"
: "${K6_IMAGE:=grafana/k6:0.49.0}"
: "${SUMMARY_NAME:=k6-web-baseline-summary.json}"

docker run --rm --network host \
  -u "$(id -u):$(id -g)" \
  -e BASE_URL="${BASE_URL}" \
  -e BASIC_USER="${BASIC_USER}" \
  -e BASIC_PASSWORD="${BASIC_PASSWORD}" \
  -e JDS_VUS="${JDS_VUS:-5}" \
  -e CANDIDATES_VUS="${CANDIDATES_VUS:-5}" \
  -e SEARCH_MISS_VUS="${SEARCH_MISS_VUS:-3}" \
  -e SEARCH_HIT_VUS="${SEARCH_HIT_VUS:-3}" \
  -e JDS_DURATION="${JDS_DURATION:-30s}" \
  -e CANDIDATES_DURATION="${CANDIDATES_DURATION:-30s}" \
  -e SEARCH_MISS_DURATION="${SEARCH_MISS_DURATION:-30s}" \
  -e SEARCH_HIT_DURATION="${SEARCH_HIT_DURATION:-30s}" \
  -v "${ROOT_DIR}:/work" \
  -v "${RESULT_DIR}:/results" \
  -w /work \
  "${K6_IMAGE}" run \
  --summary-export "/results/${SUMMARY_NAME}" \
  "${SCRIPT_PATH}"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d)"
STUB_DIR="${TMP_ROOT}/stubs"
LOG_DIR="${TMP_ROOT}/logs"
NOHUP_LOG="${LOG_DIR}/nohup.log"
DOCKER_LOG="${LOG_DIR}/docker.log"
NPM_LOG="${LOG_DIR}/npm.log"
CURL_LOG="${LOG_DIR}/curl.log"

cleanup() {
  if [[ -x "${TMP_ROOT}/scripts/local-run.sh" ]]; then
    PATH="${STUB_DIR}:${PATH}" "${TMP_ROOT}/scripts/local-run.sh" stop >/dev/null 2>&1 || true
  fi
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

mkdir -p "${STUB_DIR}" "${LOG_DIR}"

ln -s "${ROOT_DIR}/backend" "${TMP_ROOT}/backend"
ln -s "${ROOT_DIR}/frontend" "${TMP_ROOT}/frontend"
ln -s "${ROOT_DIR}/Qwen" "${TMP_ROOT}/Qwen"
ln -s "${ROOT_DIR}/scripts" "${TMP_ROOT}/scripts"
ln -s "${ROOT_DIR}/docker-compose.yml" "${TMP_ROOT}/docker-compose.yml"
ln -s "${ROOT_DIR}/.env" "${TMP_ROOT}/.env"

cat >"${STUB_DIR}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

log_file="${DOCKER_LOG_FILE:-}"
if [[ -n "${log_file}" ]]; then
  printf '%s\n' "docker $*" >>"${log_file}"
fi

case "${1:-}" in
  compose)
    if [[ -z "${CVECT_BASIC_AUTH_USERNAME:-}" || -z "${CVECT_BASIC_AUTH_PASSWORD:-}" ]]; then
      echo "missing compose basic auth interpolation fallback" >&2
      exit 17
    fi
    if [[ " $* " == *" ps -q postgres "* ]]; then
      printf 'fake-postgres-id\n'
    fi
    exit 0
    ;;
  inspect)
    printf 'healthy\n'
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
EOF

cat >"${STUB_DIR}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

log_file="${CURL_LOG_FILE:-}"
if [[ -n "${log_file}" ]]; then
  printf '%s\n' "curl $*" >>"${log_file}"
fi
exit 0
EOF

cat >"${STUB_DIR}/python3" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "-" ]]; then
  cat >/dev/null
fi
exit 0
EOF

cat >"${STUB_DIR}/npm" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

log_file="${NPM_LOG_FILE:-}"
if [[ -n "${log_file}" ]]; then
  printf '%s\n' "npm $*" >>"${log_file}"
fi
exit 0
EOF

cat >"${STUB_DIR}/nohup" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

log_file="${NOHUP_LOG_FILE:-}"
if [[ -n "${log_file}" ]]; then
  {
    printf '%s\n' "CALL"
    printf '%s\n' "$@"
    printf '%s\n' "END"
  } >>"${log_file}"
fi

exec sleep 300
EOF

chmod +x "${STUB_DIR}/docker" "${STUB_DIR}/curl" "${STUB_DIR}/python3" "${STUB_DIR}/npm" "${STUB_DIR}/nohup"

export PATH="${STUB_DIR}:${PATH}"
export NOHUP_LOG_FILE="${NOHUP_LOG}"
export DOCKER_LOG_FILE="${DOCKER_LOG}"
export CURL_LOG_FILE="${CURL_LOG}"
export NPM_LOG_FILE="${NPM_LOG}"

export CVECT_SECURITY_ENABLED="false"
export CVECT_JWT_SECRET="local-run-secret"
export CVECT_JWT_TTL_SECONDS="12345"
export CVECT_CACHE_ENABLED="false"
export CVECT_EMBEDDING_MAX_INPUT_LENGTH="2048"
export CVECT_EMBEDDING_TIMEOUT_SECONDS="321"
export CVECT_UPLOAD_STORAGE_DIR="/tmp/cvect-storage"
export CVECT_SHOW_SQL="true"
export CVECT_VECTOR_MAX_CONCURRENT_WRITES="7"
export CVECT_HF_HUB_OFFLINE="false"
export CVECT_HF_LOCAL_FILES_ONLY="false"
export CVECT_EMBEDDING_SERVICE_URL="http://example.invalid/embed"

"${TMP_ROOT}/scripts/local-run.sh" start >/dev/null 2>&1

assert_contains() {
  local file="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$file"; then
    echo "Expected '$pattern' in $file" >&2
    exit 1
  fi
}

assert_contains "${NOHUP_LOG}" "CVECT_SECURITY_ENABLED=false"
assert_contains "${NOHUP_LOG}" "CVECT_JWT_SECRET=local-run-secret"
assert_contains "${NOHUP_LOG}" "CVECT_JWT_TTL_SECONDS=12345"
assert_contains "${NOHUP_LOG}" "CVECT_CACHE_ENABLED=false"
assert_contains "${NOHUP_LOG}" "CVECT_EMBEDDING_MAX_INPUT_LENGTH=2048"
assert_contains "${NOHUP_LOG}" "CVECT_EMBEDDING_TIMEOUT_SECONDS=321"
assert_contains "${NOHUP_LOG}" "CVECT_UPLOAD_STORAGE_DIR=/tmp/cvect-storage"
assert_contains "${NOHUP_LOG}" "CVECT_SHOW_SQL=true"
assert_contains "${NOHUP_LOG}" "CVECT_VECTOR_MAX_CONCURRENT_WRITES=7"
assert_contains "${NOHUP_LOG}" "HF_HUB_OFFLINE=false"
assert_contains "${NOHUP_LOG}" "HF_LOCAL_FILES_ONLY=false"
assert_contains "${NOHUP_LOG}" "HF_HOME=${TMP_ROOT}/.runtime/huggingface"
assert_contains "${NOHUP_LOG}" "CVECT_EMBEDDING_SERVICE_URL=http://example.invalid/embed"
assert_contains "${CURL_LOG}" "curl --noproxy * --max-time 5 -fsS http://localhost:8001/ready"
assert_contains "${DOCKER_LOG}" "-f ${TMP_ROOT}/.run/docker-compose.local.yml"
assert_contains "${TMP_ROOT}/.run/docker-compose.local.yml" "- \"5432:5432\""

echo "local-run env propagation check passed"

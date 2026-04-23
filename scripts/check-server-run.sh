#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d)"
STUB_DIR="${TMP_ROOT}/stubs"
LOG_DIR="${TMP_ROOT}/logs"
DOCKER_LOG="${LOG_DIR}/docker.log"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

mkdir -p "${STUB_DIR}" "${LOG_DIR}"

ln -s "${ROOT_DIR}/scripts" "${TMP_ROOT}/scripts"
ln -s "${ROOT_DIR}/docker-compose.web.yml" "${TMP_ROOT}/docker-compose.web.yml"
ln -s "${ROOT_DIR}/.env.web" "${TMP_ROOT}/.env.web"

cat >"${STUB_DIR}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

log_file="${DOCKER_LOG_FILE:-}"
if [[ -n "${log_file}" ]]; then
  printf '%s\n' "docker $*" >>"${log_file}"
fi
exit 0
EOF

chmod +x "${STUB_DIR}/docker"

export PATH="${STUB_DIR}:${PATH}"
export DOCKER_LOG_FILE="${DOCKER_LOG}"
export CVECT_EMBEDDING_SERVICE_URL="http://embedding.example:8001/embed"
export CVECT_EMBEDDING_HEALTH_URL="http://embedding.example:8001/ready"

"${TMP_ROOT}/scripts/server-run.sh" up >/dev/null 2>&1

if ! grep -Fq -- "--no-build" "${DOCKER_LOG}"; then
  echo "Expected server-run up to use --no-build" >&2
  exit 1
fi

if grep -Fq -- "--build" "${DOCKER_LOG}"; then
  echo "Did not expect server-run up to use --build" >&2
  exit 1
fi

if grep -Fq -- " qwen" "${DOCKER_LOG}"; then
  echo "Did not expect server-run up to include qwen" >&2
  exit 1
fi

if ! grep -Fq -- " postgres backend frontend" "${DOCKER_LOG}"; then
  echo "Expected server-run up to target the web services only" >&2
  exit 1
fi

: >"${DOCKER_LOG}"

"${TMP_ROOT}/scripts/server-run.sh" up-build >/dev/null 2>&1

if ! grep -Fq -- "--build" "${DOCKER_LOG}"; then
  echo "Expected server-run up-build to use --build" >&2
  exit 1
fi

if grep -Fq -- " qwen" "${DOCKER_LOG}"; then
  echo "Did not expect server-run up-build to include qwen" >&2
  exit 1
fi

if CVECT_EMBEDDING_SERVICE_URL="http://qwen:8001/embed" \
  CVECT_EMBEDDING_HEALTH_URL="http://qwen:8001/ready" \
  "${TMP_ROOT}/scripts/server-run.sh" up >"${LOG_DIR}/guard.log" 2>&1; then
  echo "Expected server-run up to reject local qwen URLs" >&2
  exit 1
fi

if ! grep -Fq -- "manages only the web stack" "${LOG_DIR}/guard.log"; then
  echo "Expected server-run guard error for local qwen URLs" >&2
  exit 1
fi

echo "server-run web split check passed"

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
ln -s "${ROOT_DIR}/docker-compose.yml" "${TMP_ROOT}/docker-compose.yml"
ln -s "${ROOT_DIR}/.env" "${TMP_ROOT}/.env"

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
export CVECT_HF_HUB_OFFLINE="false"
export CVECT_HF_LOCAL_FILES_ONLY="false"

"${TMP_ROOT}/scripts/server-run.sh" up >/dev/null 2>&1

if ! grep -Fq -- "--no-build" "${DOCKER_LOG}"; then
  echo "Expected server-run up to use --no-build" >&2
  exit 1
fi

if grep -Fq -- "--build" "${DOCKER_LOG}"; then
  echo "Did not expect server-run up to use --build" >&2
  exit 1
fi

: >"${DOCKER_LOG}"

"${TMP_ROOT}/scripts/server-run.sh" up-build >/dev/null 2>&1

if ! grep -Fq -- "--build" "${DOCKER_LOG}"; then
  echo "Expected server-run up-build to use --build" >&2
  exit 1
fi

echo "server-run build mode check passed"

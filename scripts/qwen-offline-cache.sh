#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
COMMAND="${1:-help}"
ARG1="${2:-}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Create or restore it first." >&2
  exit 1
fi

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

resolve_path() {
  local path="$1"
  if [[ "${path}" = /* ]]; then
    printf '%s' "${path}"
  else
    printf '%s/%s' "${ROOT_DIR}" "${path#./}"
  fi
}

ensure_hf_tooling() {
  local venv_dir python_bin pip_bin pip_index_url trusted_host
  local -a install_cmd

  if python3 -c "import huggingface_hub" >/dev/null 2>&1; then
    PYTHON_BIN="python3"
    return 0
  fi

  venv_dir="${ROOT_DIR}/.runtime/hf-tools-venv"
  python_bin="${venv_dir}/bin/python"
  pip_bin="${venv_dir}/bin/pip"
  pip_index_url="${CVECT_PIP_INDEX_URL:-$(read_env_value CVECT_PIP_INDEX_URL)}"
  trusted_host="${CVECT_PIP_TRUSTED_HOST:-$(read_env_value CVECT_PIP_TRUSTED_HOST)}"

  mkdir -p "${ROOT_DIR}/.runtime"
  if [[ ! -x "${python_bin}" ]]; then
    python3 -m venv "${venv_dir}"
  fi

  install_cmd=("${pip_bin}" install --quiet)
  if [[ -n "${pip_index_url}" ]]; then
    install_cmd+=(--index-url "${pip_index_url}")
  fi
  if [[ -n "${trusted_host}" ]]; then
    install_cmd+=(--trusted-host "${trusted_host}")
  fi
  install_cmd+=(huggingface_hub)
  "${install_cmd[@]}"
  PYTHON_BIN="${python_bin}"
}

hf_cache_dir() {
  local configured
  configured="${CVECT_HF_CACHE_DIR:-$(read_env_value CVECT_HF_CACHE_DIR)}"
  if [[ -z "${configured}" ]]; then
    configured="./.runtime/huggingface"
  fi
  resolve_path "${configured}"
}

hf_cache_archive() {
  local configured
  configured="${CVECT_HF_CACHE_ARCHIVE:-$(read_env_value CVECT_HF_CACHE_ARCHIVE)}"
  if [[ -z "${configured}" ]]; then
    configured="./.artifacts/qwen-hf-cache.tgz"
  fi
  resolve_path "${configured}"
}

embedding_model() {
  printf '%s' "${CVECT_EMBEDDING_MODEL:-$(read_env_value CVECT_EMBEDDING_MODEL)}"
}

hf_endpoint() {
  printf '%s' "${CVECT_HF_ENDPOINT:-$(read_env_value CVECT_HF_ENDPOINT)}"
}

export_proxy_env() {
  export HF_ENDPOINT="${CVECT_HF_ENDPOINT:-$(read_env_value CVECT_HF_ENDPOINT)}"
  export HF_HUB_DISABLE_XET="${CVECT_HF_HUB_DISABLE_XET:-$(read_env_value CVECT_HF_HUB_DISABLE_XET)}"
  export HTTP_PROXY="${CVECT_HTTP_PROXY:-$(read_env_value CVECT_HTTP_PROXY)}"
  export HTTPS_PROXY="${CVECT_HTTPS_PROXY:-$(read_env_value CVECT_HTTPS_PROXY)}"
  export NO_PROXY="${CVECT_NO_PROXY:-$(read_env_value CVECT_NO_PROXY)}"
  export http_proxy="${HTTP_PROXY:-}"
  export https_proxy="${HTTPS_PROXY:-}"
  export no_proxy="${NO_PROXY:-}"
}

prefetch_models() {
  local cache_dir embedding
  cache_dir="$(hf_cache_dir)"
  embedding="$(embedding_model)"

  if [[ -z "${embedding}" ]]; then
    echo "Missing CVECT_EMBEDDING_MODEL in ${ENV_FILE}" >&2
    exit 1
  fi

  mkdir -p "${cache_dir}"
  export_proxy_env
  export HF_HOME="${cache_dir}"
  export HF_HUB_OFFLINE=false
  export HF_LOCAL_FILES_ONLY=false

  ensure_hf_tooling

  "${PYTHON_BIN}" - <<'PY' "${embedding}"
import json
import os
import pathlib
import sys
from datetime import datetime, timezone

from huggingface_hub import snapshot_download

models = list(dict.fromkeys(sys.argv[1:]))
cache_root = pathlib.Path(os.environ["HF_HOME"])
manifest = {
    "downloaded_at": datetime.now(timezone.utc).isoformat(),
    "hf_endpoint": os.environ.get("HF_ENDPOINT", ""),
    "models": [],
}

for model_id in models:
    snapshot_path = snapshot_download(repo_id=model_id, repo_type="model", resume_download=True)
    manifest["models"].append({"model_id": model_id, "snapshot_path": snapshot_path})
    print(f"downloaded {model_id} -> {snapshot_path}")

manifest_path = cache_root / "cvect-offline-manifest.json"
manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
print(f"manifest written to {manifest_path}")
PY

  echo
  echo "Prefetch completed."
  echo "Cache dir: ${cache_dir}"
}

pack_cache() {
  local cache_dir archive
  cache_dir="$(hf_cache_dir)"
  archive="${ARG1:-$(hf_cache_archive)}"
  archive="$(resolve_path "${archive}")"

  if [[ ! -d "${cache_dir}" ]]; then
    echo "Cache directory does not exist: ${cache_dir}" >&2
    exit 1
  fi

  mkdir -p "$(dirname "${archive}")"
  tar -C "${cache_dir}" -czf "${archive}" .
  echo "Archive created: ${archive}"
}

unpack_cache() {
  local archive cache_dir
  archive="${ARG1:-$(hf_cache_archive)}"
  archive="$(resolve_path "${archive}")"
  cache_dir="$(hf_cache_dir)"

  if [[ ! -f "${archive}" ]]; then
    echo "Archive not found: ${archive}" >&2
    exit 1
  fi

  mkdir -p "${cache_dir}"
  tar -xzf "${archive}" -C "${cache_dir}"
  echo "Archive unpacked to: ${cache_dir}"
}

verify_cache() {
  local cache_dir embedding
  cache_dir="$(hf_cache_dir)"
  embedding="$(embedding_model)"

  if [[ ! -d "${cache_dir}" ]]; then
    echo "Cache directory does not exist: ${cache_dir}" >&2
    exit 1
  fi

  export HF_HOME="${cache_dir}"
  export HF_HUB_OFFLINE=true
  export HF_LOCAL_FILES_ONLY=true
  ensure_hf_tooling

  "${PYTHON_BIN}" - <<'PY' "${embedding}"
import os
import sys
from huggingface_hub import snapshot_download

for model_id in dict.fromkeys(sys.argv[1:]):
    snapshot_path = snapshot_download(repo_id=model_id, repo_type="model", local_files_only=True)
    print(f"verified {model_id} -> {snapshot_path}")
print(f"cache_root={os.environ['HF_HOME']}")
PY
}

show_info() {
  cat <<EOF
HF endpoint:      $(hf_endpoint)
Embedding model:  $(embedding_model)
Cache dir:        $(hf_cache_dir)
Archive path:     $(hf_cache_archive)
EOF
}

case "${COMMAND}" in
  prefetch)
    prefetch_models
    ;;
  pack)
    pack_cache
    ;;
  unpack)
    unpack_cache
    ;;
  verify)
    verify_cache
    ;;
  info)
    show_info
    ;;
  help|--help|-h)
    cat <<EOF
Usage: scripts/qwen-offline-cache.sh [prefetch|pack|unpack|verify|info] [archive]

Commands:
  prefetch   Download the embedding model into CVECT_HF_CACHE_DIR.
  pack       Create a tar.gz archive from the local Hugging Face cache.
  unpack     Extract an archive into CVECT_HF_CACHE_DIR on the target machine.
  verify     Check that the embedding model can be resolved from the offline cache only.
  info       Print the current model/cache configuration.

Examples:
  scripts/qwen-offline-cache.sh prefetch
  scripts/qwen-offline-cache.sh verify
  scripts/qwen-offline-cache.sh pack
  scp .artifacts/qwen-hf-cache.tgz <server>:/tmp/
  # on the server:
  scripts/qwen-offline-cache.sh unpack /tmp/qwen-hf-cache.tgz
  scripts/server-run.sh up
EOF
    ;;
  *)
    echo "Unknown command: ${COMMAND}" >&2
    exit 1
    ;;
esac

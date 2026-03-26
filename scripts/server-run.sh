#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"
ENV_FILE="${ROOT_DIR}/.env"
COMMAND="${1:-up}"
SERVICE="${2:-}"

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

normalize_bool() {
  local value="${1:-}"
  value="$(printf '%s' "${value}" | tr '[:upper:]' '[:lower:]')"
  case "${value}" in
    1|true|yes|on)
      printf 'true'
      ;;
    *)
      printf 'false'
      ;;
  esac
}

resolve_path() {
  local path="$1"
  if [[ "${path}" = /* ]]; then
    printf '%s' "${path}"
  else
    printf '%s/%s' "${ROOT_DIR}" "${path#./}"
  fi
}

prepare_hf_cache_dir() {
  local cache_dir offline local_only cache_dir_abs

  cache_dir="${CVECT_HF_CACHE_DIR:-$(read_env_value CVECT_HF_CACHE_DIR)}"
  offline="$(normalize_bool "${CVECT_HF_HUB_OFFLINE:-$(read_env_value CVECT_HF_HUB_OFFLINE)}")"
  local_only="$(normalize_bool "${CVECT_HF_LOCAL_FILES_ONLY:-$(read_env_value CVECT_HF_LOCAL_FILES_ONLY)}")"

  if [[ -z "${cache_dir}" ]]; then
    echo "Missing CVECT_HF_CACHE_DIR in ${ENV_FILE}" >&2
    exit 1
  fi

  cache_dir_abs="$(resolve_path "${cache_dir}")"
  mkdir -p "${cache_dir_abs}"

  if [[ "${offline}" = "true" || "${local_only}" = "true" ]]; then
    if [[ ! -d "${cache_dir_abs}/hub" ]] || ! find "${cache_dir_abs}/hub" -mindepth 1 -maxdepth 1 -type d | grep -q .; then
      cat >&2 <<EOF
Offline Hugging Face mode is enabled, but the cache is empty:
  ${cache_dir_abs}

Prepare the cache locally first:
  scripts/qwen-offline-cache.sh prefetch
  scripts/qwen-offline-cache.sh pack

Then upload the archive to the server and unpack it:
  scripts/qwen-offline-cache.sh unpack /path/to/qwen-hf-cache.tgz
EOF
      exit 1
    fi
  fi
}

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "Missing compose file: ${COMPOSE_FILE}"
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing env file: ${ENV_FILE}"
  exit 1
fi

run_compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

run_up() {
  local build_flag="${1:-}"
  shift || true

  if uses_local_qwen; then
    prepare_hf_cache_dir
    if [[ -n "${build_flag}" ]]; then
      run_compose up -d "${build_flag}" "$@"
    else
      run_compose up -d "$@"
    fi
  else
    if [[ -n "${build_flag}" ]]; then
      run_compose up -d "${build_flag}" "$@" postgres backend frontend
    else
      run_compose up -d "$@" postgres backend frontend
    fi
  fi
}

embedding_service_url() {
  local value
  value="${CVECT_EMBEDDING_SERVICE_URL:-$(read_env_value CVECT_EMBEDDING_SERVICE_URL)}"
  if [[ -z "${value}" ]]; then
    value="http://qwen:8001/embed"
  fi
  printf '%s' "${value}"
}

uses_local_qwen() {
  local url
  url="$(embedding_service_url)"
  [[ "${url}" =~ ^https?://qwen(:[0-9]+)?(/.*)?$ ]]
}

case "${COMMAND}" in
  up)
    run_up --build
    ;;
  up-no-build)
    run_up --no-build
    ;;
  down)
    run_compose down
    ;;
  restart)
    run_up --build --force-recreate
    ;;
  restart-no-build)
    run_up --no-build --force-recreate
    ;;
  status)
    run_compose ps
    ;;
  logs)
    if [[ -n "${SERVICE}" ]]; then
      run_compose logs -f "${SERVICE}"
    else
      run_compose logs -f
    fi
    ;;
  config)
    run_compose config
    ;;
  pull)
    run_compose pull
    ;;
  *)
    cat <<EOF
Usage: scripts/server-run.sh [up|up-no-build|down|restart|restart-no-build|status|logs|config|pull] [service]

Examples:
  scripts/qwen-offline-cache.sh prefetch
  scripts/server-run.sh up
  scripts/server-run.sh up-no-build
  scripts/server-run.sh status
  scripts/server-run.sh logs backend
EOF
    exit 1
    ;;
esac

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${CVECT_COMPOSE_FILE:-${ROOT_DIR}/docker-compose.embedding.yml}"
ENV_FILE="${CVECT_ENV_FILE:-${ROOT_DIR}/.env.embedding}"
COMMAND="${1:-up}"
SERVICE="${2:-}"
COMPOSE_PROJECT_NAME_VALUE="${CVECT_EMBEDDING_COMPOSE_PROJECT_NAME:-cvect-embedding}"
EMBEDDING_SERVICES=(qwen)

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

Then upload the archive to the embedding host and unpack it:
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
  CVECT_COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME_VALUE}" \
    docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

run_embedding_up() {
  local build_flag="${1:-}"
  shift || true

  prepare_hf_cache_dir

  if [[ -n "${build_flag}" ]]; then
    run_compose up -d "${build_flag}" "$@" "${EMBEDDING_SERVICES[@]}"
  else
    run_compose up -d "$@" "${EMBEDDING_SERVICES[@]}"
  fi
}

case "${COMMAND}" in
  up)
    run_embedding_up --no-build
    ;;
  up-no-build)
    run_embedding_up --no-build
    ;;
  up-build)
    run_embedding_up --build
    ;;
  down)
    run_compose down
    ;;
  restart)
    run_embedding_up --no-build --force-recreate
    ;;
  restart-no-build)
    run_embedding_up --no-build --force-recreate
    ;;
  restart-build)
    run_embedding_up --build --force-recreate
    ;;
  status)
    run_compose ps "${EMBEDDING_SERVICES[@]}"
    ;;
  logs)
    if [[ -n "${SERVICE}" ]]; then
      run_compose logs -f "${SERVICE}"
    else
      run_compose logs -f "${EMBEDDING_SERVICES[@]}"
    fi
    ;;
  config)
    run_compose config
    ;;
  pull)
    run_compose pull "${EMBEDDING_SERVICES[@]}"
    ;;
  *)
    cat <<EOF
Usage: scripts/embedding-run.sh [up|up-build|up-no-build|down|restart|restart-build|restart-no-build|status|logs|config|pull] [service]

Examples:
  scripts/qwen-offline-cache.sh pack
  scripts/embedding-run.sh up
  scripts/embedding-run.sh up-build
  scripts/embedding-run.sh status
  scripts/embedding-run.sh logs

Notes:
  - This script manages only the embedding stack: ${EMBEDDING_SERVICES[*]}
  - Default env file: ${ENV_FILE}
  - CVECT_EMBEDDING_PUBLIC_PORT controls the published qwen port on the GPU host
EOF
    exit 1
    ;;
esac

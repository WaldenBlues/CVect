#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.prod.yml"
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
  echo "Create it with:"
  echo "  cp ${ROOT_DIR}/.env.example ${ROOT_DIR}/.env"
  exit 1
fi

run_compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

case "${COMMAND}" in
  up)
    prepare_hf_cache_dir
    run_compose up -d --build
    ;;
  down)
    run_compose down
    ;;
  restart)
    prepare_hf_cache_dir
    run_compose up -d --build --force-recreate
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
Usage: scripts/cloud-deploy.sh [up|down|restart|status|logs|config|pull] [service]

Examples:
  cp .env.example .env
  scripts/qwen-offline-cache.sh prefetch
  scripts/cloud-deploy.sh up
  scripts/cloud-deploy.sh status
  scripts/cloud-deploy.sh logs backend
EOF
    exit 1
    ;;
esac

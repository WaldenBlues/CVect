#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${CVECT_COMPOSE_FILE:-${ROOT_DIR}/docker-compose.web.yml}"
ENV_FILE="${CVECT_ENV_FILE:-${ROOT_DIR}/.env.web}"
COMMAND="${1:-up}"
SERVICE="${2:-}"
COMPOSE_PROJECT_NAME_VALUE="${CVECT_WEB_COMPOSE_PROJECT_NAME:-cvect-web}"
WEB_SERVICES=(postgres backend frontend)

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

embedding_service_url() {
  local value
  value="${CVECT_EMBEDDING_SERVICE_URL:-$(read_env_value CVECT_EMBEDDING_SERVICE_URL)}"
  if [[ -z "${value}" ]]; then
    value="http://qwen:8001/embed"
  fi
  printf '%s' "${value}"
}

embedding_health_url() {
  local url
  url="${CVECT_EMBEDDING_HEALTH_URL:-$(read_env_value CVECT_EMBEDDING_HEALTH_URL)}"
  if [[ -z "${url}" ]]; then
    url="http://qwen:8001/ready"
  fi
  printf '%s' "${url}"
}

is_local_qwen_url() {
  local url="$1"
  [[ "${url}" =~ ^https?://qwen(:[0-9]+)?(/.*)?$ ]]
}

ensure_external_embedding_service() {
  local service_url health_url
  service_url="$(embedding_service_url)"
  health_url="$(embedding_health_url)"

  if is_local_qwen_url "${service_url}" || is_local_qwen_url "${health_url}"; then
    cat >&2 <<EOF
scripts/server-run.sh now manages only the web stack: ${WEB_SERVICES[*]}

Current embedding config still points to the in-project qwen service:
  CVECT_EMBEDDING_SERVICE_URL=${service_url}
  CVECT_EMBEDDING_HEALTH_URL=${health_url}

Set those env values to your external embedding host before starting the web stack.
Run scripts/embedding-run.sh on the GPU host if you want to deploy qwen itself.
EOF
    exit 1
  fi
}

run_web_up() {
  local build_flag="${1:-}"
  shift || true

  ensure_external_embedding_service

  if [[ -n "${build_flag}" ]]; then
    run_compose up -d "${build_flag}" "$@" "${WEB_SERVICES[@]}"
  else
    run_compose up -d "$@" "${WEB_SERVICES[@]}"
  fi
}

case "${COMMAND}" in
  up)
    run_web_up --no-build
    ;;
  up-no-build)
    run_web_up --no-build
    ;;
  up-build)
    run_web_up --build
    ;;
  down)
    run_compose down
    ;;
  restart)
    run_web_up --no-build --force-recreate
    ;;
  restart-no-build)
    run_web_up --no-build --force-recreate
    ;;
  restart-build)
    run_web_up --build --force-recreate
    ;;
  status)
    run_compose ps "${WEB_SERVICES[@]}"
    ;;
  logs)
    if [[ -n "${SERVICE}" ]]; then
      run_compose logs -f "${SERVICE}"
    else
      run_compose logs -f "${WEB_SERVICES[@]}"
    fi
    ;;
  config)
    run_compose config
    ;;
  pull)
    run_compose pull "${WEB_SERVICES[@]}"
    ;;
  *)
    cat <<EOF
Usage: scripts/server-run.sh [up|up-build|up-no-build|down|restart|restart-build|restart-no-build|status|logs|config|pull] [service]

Examples:
  scripts/server-run.sh up
  scripts/server-run.sh up-build
  scripts/server-run.sh restart
  scripts/server-run.sh status
  scripts/server-run.sh logs backend

Notes:
  - This script manages only the web stack: ${WEB_SERVICES[*]}
  - Default env file: ${ENV_FILE}
  - Point CVECT_EMBEDDING_SERVICE_URL / CVECT_EMBEDDING_HEALTH_URL at your external embedding service
  - Use scripts/embedding-run.sh on the GPU host for qwen
EOF
    exit 1
    ;;
esac

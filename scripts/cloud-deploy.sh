#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.prod.yml"
DEFAULT_ENV_FILE="${ROOT_DIR}/.env.cloud"
if [[ -f "${ROOT_DIR}/.env.cn" ]]; then
  DEFAULT_ENV_FILE="${ROOT_DIR}/.env.cn"
fi
if [[ -f "${ROOT_DIR}/.env.demo" ]]; then
  DEFAULT_ENV_FILE="${ROOT_DIR}/.env.demo"
fi
ENV_FILE="${CVECT_ENV_FILE:-${DEFAULT_ENV_FILE}}"
COMMAND="${1:-up}"
SERVICE="${2:-}"

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "Missing compose file: ${COMPOSE_FILE}"
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing env file: ${ENV_FILE}"
  echo "Create it with:"
  echo "  cp ${ROOT_DIR}/.env.demo.example ${ROOT_DIR}/.env.demo"
  echo "  cp ${ROOT_DIR}/.env.cn.example ${ROOT_DIR}/.env.cn"
  echo "  cp ${ROOT_DIR}/.env.cloud.example ${ENV_FILE}"
  exit 1
fi

run_compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

case "${COMMAND}" in
  up)
    run_compose up -d --build
    ;;
  down)
    run_compose down
    ;;
  restart)
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
  cp .env.demo.example .env.demo
  cp .env.cn.example .env.cn
  scripts/cloud-deploy.sh up
  scripts/cloud-deploy.sh status
  scripts/cloud-deploy.sh logs backend
EOF
    exit 1
    ;;
esac

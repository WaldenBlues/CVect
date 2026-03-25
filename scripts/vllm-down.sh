#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
cd "$ROOT_DIR"

if [[ -f "${ENV_FILE}" ]]; then
  docker compose --env-file "${ENV_FILE}" -f infra/vllm/docker-compose.yml down
else
  docker compose -f infra/vllm/docker-compose.yml down
fi

echo "vLLM stack stopped."

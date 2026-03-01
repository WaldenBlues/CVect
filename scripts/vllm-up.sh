#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

docker compose -f infra/vllm/docker-compose.yml up -d

echo "vLLM stack started."
echo "- LLM:      http://localhost:8000"
echo "- Embedding:http://localhost:8001"
echo "- Gateway:  http://localhost:8002"

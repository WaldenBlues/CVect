#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8002}"
EMB_MODEL="${EMB_MODEL:-Qwen/Qwen3-Embedding-0.6B}"
CHAT_MODEL="${CHAT_MODEL:-Qwen/Qwen3-0.6B}"

wait_for_gateway() {
  echo "[1/3] Waiting for gateway at ${BASE_URL}/v1/models ..."
  for _ in $(seq 1 120); do
    if curl -fsS "${BASE_URL}/v1/models" >/dev/null 2>&1; then
      echo "Gateway is ready."
      return 0
    fi
    sleep 2
  done
  echo "Gateway is not ready within timeout." >&2
  return 1
}

test_embeddings() {
  echo "[2/3] Testing embeddings endpoint ..."
  local resp
  resp="$(curl -fsS -H 'Content-Type: application/json' \
    -X POST "${BASE_URL}/v1/embeddings" \
    -d "{\"model\":\"${EMB_MODEL}\",\"input\":[\"hello world\",\"resume matching\"]}")"

  python3 - << 'PY' "$resp"
import json, sys
obj = json.loads(sys.argv[1])
vec = obj["data"][0]["embedding"]
print(f"Embedding length: {len(vec)}")
if len(vec) <= 0:
    raise SystemExit("Embedding vector is empty")
PY
}

test_chat() {
  echo "[3/3] Testing chat completions endpoint ..."
  local resp
  resp="$(curl -fsS -H 'Content-Type: application/json' \
    -X POST "${BASE_URL}/v1/chat/completions" \
    -d "{\"model\":\"${CHAT_MODEL}\",\"messages\":[{\"role\":\"user\",\"content\":\"Say hi in one short sentence.\"}],\"max_tokens\":64,\"temperature\":0.2}")"

  python3 - << 'PY' "$resp"
import json, sys
obj = json.loads(sys.argv[1])
content = obj.get("choices", [{}])[0].get("message", {}).get("content", "")
print(f"Chat content: {content!r}")
if not content or not content.strip():
    raise SystemExit("Chat content is empty")
PY
}

wait_for_gateway
test_embeddings
test_chat

echo "Smoke test passed."

#!/usr/bin/env bash
set -euo pipefail

assert_contains() {
  local file="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$file"; then
    echo "Expected '$pattern' in $file" >&2
    exit 1
  fi
}

assert_not_contains() {
  local file="$1"
  local pattern="$2"
  if grep -Fq -- "$pattern" "$file"; then
    echo "Did not expect '$pattern' in $file" >&2
    exit 1
  fi
}

assert_contains backend/cvect/Dockerfile "USER cvect"
assert_contains backend/cvect/Dockerfile "/data/storage"
assert_contains Qwen/Dockerfile "USER qwen"
assert_contains Qwen/Dockerfile "HF_HOME=\"/home/qwen/.cache/huggingface\""
assert_contains Qwen/Dockerfile "--retries 10 --timeout 120"
assert_contains frontend/Dockerfile "USER nginx"
assert_contains frontend/Dockerfile "/etc/nginx/nginx.conf"
assert_contains frontend/nginx-rootless.conf "pid /tmp/nginx.pid;"
assert_contains frontend/nginx-rootless.conf "access_log /dev/stdout main;"
assert_contains frontend/nginx.conf "listen 8080;"
assert_contains frontend/docker-entrypoint.d/30-cvect-auth.envsh "htpasswd -ic /tmp/cvect-auth/.htpasswd"
assert_contains frontend/docker-entrypoint.d/30-cvect-auth.envsh "auth_basic_user_file /tmp/cvect-auth/.htpasswd;"
assert_contains docker-compose.yml '${CVECT_HTTP_PORT:?set in .env}:8080'
assert_contains docker-compose.yml "/home/qwen/.cache/huggingface"
assert_contains docker-compose.yml "CVECT_UPLOAD_WORKER_ENABLED"
assert_contains docker-compose.yml "TORCH_PACKAGE: \${CVECT_TORCH_PACKAGE:-torch==2.10.0}"
assert_contains docker-compose.web.yml '${CVECT_EMBEDDING_SERVICE_URL:?set in .env.web}'
assert_contains docker-compose.embedding.yml '${CVECT_EMBEDDING_PUBLIC_PORT:-8001}:8001'
assert_contains .env "CVECT_BASIC_AUTH_ENABLED=true"
assert_contains .env "CVECT_BASIC_AUTH_USERNAME="
assert_contains .env "CVECT_BASIC_AUTH_PASSWORD="
assert_contains .env "CVECT_TORCH_PACKAGE=torch==2.10.0"
assert_not_contains .env.web "http://qwen:8001"
assert_contains .env.embedding "CVECT_EMBEDDING_PUBLIC_PORT=8001"
assert_not_contains .env "demo123"
assert_not_contains scripts/perf/run-k6-web-baseline.sh "demo123"
assert_not_contains scripts/perf/k6-web-baseline.js "demo123"

docker compose --env-file .env -f docker-compose.yml config >/dev/null
docker compose --env-file .env.web -f docker-compose.web.yml config >/dev/null
docker compose --env-file .env.embedding -f docker-compose.embedding.yml config >/dev/null

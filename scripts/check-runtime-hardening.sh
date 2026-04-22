#!/usr/bin/env bash
set -euo pipefail

assert_contains() {
  local file="$1"
  local pattern="$2"
  if ! grep -Fq "$pattern" "$file"; then
    echo "Expected '$pattern' in $file" >&2
    exit 1
  fi
}

assert_contains backend/cvect/Dockerfile "USER cvect"
assert_contains backend/cvect/Dockerfile "/data/storage"
assert_contains Qwen/Dockerfile "USER qwen"
assert_contains Qwen/Dockerfile "HF_HOME=\"/home/qwen/.cache/huggingface\""
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

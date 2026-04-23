#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="${ROOT_DIR}/.run"
LOG_DIR="${RUN_DIR}/logs"
PID_DIR="${RUN_DIR}/pids"
ENV_FILE="${ROOT_DIR}/.env"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"

ENV_SOURCE=""
if [[ -f "${ENV_FILE}" ]]; then
  ENV_SOURCE="${ENV_FILE}"
fi

mkdir -p "${LOG_DIR}" "${PID_DIR}"

BACKEND_PID_FILE="${PID_DIR}/backend.pid"
FRONTEND_PID_FILE="${PID_DIR}/frontend.pid"
EMBED_PID_FILE="${PID_DIR}/embedding.pid"

BACKEND_LOG="${LOG_DIR}/backend.log"
FRONTEND_LOG="${LOG_DIR}/frontend.log"
EMBED_LOG="${LOG_DIR}/embedding.log"
BACKEND_PATTERN_1="${ROOT_DIR}/backend/cvect"
BACKEND_PATTERN_2="com.walden.cvect.CvectApplication"
FRONTEND_PATTERN="${ROOT_DIR}/frontend/node_modules/.bin/vite --host"
EMBED_PATTERN="${ROOT_DIR}/Qwen/embedding_service.py"
EMBED_PATTERN_2="python3 embedding_service.py"

read_env_value() {
  local key="$1"
  local line
  local value=""

  [[ -n "${ENV_SOURCE}" && -f "${ENV_SOURCE}" ]] || return 0

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
  done < "${ENV_SOURCE}"
}

resolve_setting() {
  local key="$1"
  local default_value="$2"
  local env_value="${!key:-}"
  local file_value=""

  if [[ -n "${env_value}" ]]; then
    printf '%s' "${env_value}"
    return 0
  fi

  file_value="$(read_env_value "${key}")"
  if [[ -n "${file_value}" ]]; then
    printf '%s' "${file_value}"
    return 0
  fi

  printf '%s' "${default_value}"
}

UPLOAD_MAX_INFLIGHT="$(resolve_setting CVECT_UPLOAD_MAX_INFLIGHT_ITEMS 2000)"
UPLOAD_MAX_FILES_PER_ZIP="$(resolve_setting CVECT_UPLOAD_MAX_FILES_PER_ZIP 2000)"
POSTGRES_WAIT_TIMEOUT="$(resolve_setting CVECT_POSTGRES_WAIT_TIMEOUT_SECONDS 60)"
EMBED_WAIT_TIMEOUT="$(resolve_setting CVECT_EMBED_WAIT_TIMEOUT_SECONDS 60)"
BACKEND_WAIT_TIMEOUT="$(resolve_setting CVECT_BACKEND_WAIT_TIMEOUT_SECONDS 180)"
FRONTEND_WAIT_TIMEOUT="$(resolve_setting CVECT_FRONTEND_WAIT_TIMEOUT_SECONDS 120)"
POSTGRES_HOST="$(resolve_setting CVECT_DB_HOST localhost)"
POSTGRES_PORT="$(resolve_setting CVECT_DB_PORT 5432)"
POSTGRES_DB="$(resolve_setting CVECT_POSTGRES_DB cvect)"
POSTGRES_USER="$(resolve_setting CVECT_POSTGRES_USER postgres)"
POSTGRES_PASSWORD="$(resolve_setting CVECT_POSTGRES_PASSWORD postgres)"
BACKEND_PORT="$(resolve_setting CVECT_SERVER_PORT 8080)"
BACKEND_DB_URL="$(resolve_setting CVECT_DB_URL "jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}")"
BACKEND_DB_USERNAME="$(resolve_setting CVECT_DB_USERNAME "${POSTGRES_USER}")"
BACKEND_DB_PASSWORD="$(resolve_setting CVECT_DB_PASSWORD "${POSTGRES_PASSWORD}")"
SECURITY_ENABLED="$(resolve_setting CVECT_SECURITY_ENABLED true)"
JWT_SECRET="$(resolve_setting CVECT_JWT_SECRET cvect-dev-secret-change-me)"
CACHE_ENABLED="$(resolve_setting CVECT_CACHE_ENABLED true)"
VECTOR_ENABLED="$(resolve_setting CVECT_VECTOR_ENABLED true)"
VECTOR_INGEST_WORKER_ENABLED="$(resolve_setting CVECT_VECTOR_INGEST_WORKER_ENABLED true)"
UPLOAD_WORKER_ENABLED="$(resolve_setting CVECT_UPLOAD_WORKER_ENABLED true)"
EMBEDDING_MODEL="$(resolve_setting CVECT_EMBEDDING_MODEL Qwen/Qwen3-Embedding-0.6B)"
EMBEDDING_SERVICE_URL="$(resolve_setting CVECT_EMBEDDING_SERVICE_URL http://localhost:8001/embed)"
EMBEDDING_API_FORMAT="$(resolve_setting CVECT_EMBEDDING_API_FORMAT auto)"
EMBEDDING_HEALTH_URL="$(resolve_setting CVECT_EMBEDDING_HEALTH_URL http://localhost:8001/ready)"
EMBEDDING_DEVICE="$(resolve_setting CVECT_EMBEDDING_DEVICE auto)"
EMBEDDING_BATCH_SIZE="$(resolve_setting CVECT_EMBEDDING_BATCH_SIZE 1)"
EMBEDDING_MAX_CONCURRENT_REQUESTS="$(resolve_setting CVECT_EMBEDDING_MAX_CONCURRENT_REQUESTS 1)"
EMBEDDING_MAX_INPUT_LENGTH="$(resolve_setting CVECT_EMBEDDING_MAX_INPUT_LENGTH 1024)"
EMBEDDING_IDLE_UNLOAD_SECONDS="$(resolve_setting CVECT_EMBEDDING_IDLE_UNLOAD_SECONDS 900)"
EMBEDDING_IDLE_CHECK_INTERVAL_SECONDS="$(resolve_setting CVECT_EMBEDDING_IDLE_CHECK_INTERVAL_SECONDS 30)"
PRELOAD_MODELS="$(resolve_setting CVECT_PRELOAD_MODELS false)"
MALLOC_ARENA_MAX="$(resolve_setting CVECT_MALLOC_ARENA_MAX 2)"
HF_CACHE_DIR="$(resolve_setting CVECT_HF_CACHE_DIR "${ROOT_DIR}/.runtime/huggingface")"
HF_ENDPOINT="$(resolve_setting CVECT_HF_ENDPOINT https://huggingface.co)"
HF_HUB_OFFLINE="$(resolve_setting CVECT_HF_HUB_OFFLINE true)"
HF_LOCAL_FILES_ONLY="$(resolve_setting CVECT_HF_LOCAL_FILES_ONLY true)"
HF_HUB_DISABLE_XET="$(resolve_setting CVECT_HF_HUB_DISABLE_XET false)"
HTTP_PROXY_VALUE="$(resolve_setting CVECT_HTTP_PROXY "")"
HTTPS_PROXY_VALUE="$(resolve_setting CVECT_HTTPS_PROXY "")"
NO_PROXY_VALUE="$(resolve_setting CVECT_NO_PROXY "127.0.0.1,localhost,postgres,qwen,backend,frontend")"
QWEN_TORCH_DTYPE="$(resolve_setting CVECT_TORCH_DTYPE auto)"
QWEN_TORCH_NUM_THREADS="$(resolve_setting CVECT_TORCH_NUM_THREADS 1)"
QWEN_TORCH_NUM_INTEROP_THREADS="$(resolve_setting CVECT_TORCH_NUM_INTEROP_THREADS 1)"
BACKEND_HEALTH_URL="${CVECT_BACKEND_HEALTH_URL:-http://localhost:${BACKEND_PORT}/api/resumes/health}"
FRONTEND_URL="${CVECT_FRONTEND_URL:-http://localhost:5173}"

run_local_compose() {
  if [[ -n "${ENV_SOURCE}" ]]; then
    docker compose --env-file "${ENV_SOURCE}" -f "${COMPOSE_FILE}" "$@"
  else
    docker compose -f "${COMPOSE_FILE}" "$@"
  fi
}

is_running() {
  local pid_file="$1"
  if [[ -f "${pid_file}" ]]; then
    local pid
    pid="$(cat "${pid_file}")"
    if [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1; then
      return 0
    fi
  fi
  return 1
}

print_log_excerpt() {
  local log_file="$1"
  if [[ -f "${log_file}" ]]; then
    echo "---- recent log: ${log_file} ----"
    tail -n 20 "${log_file}" || true
    echo "---------------------------------"
  fi
}

assert_process_alive() {
  local name="$1"
  local pid_file="$2"
  local log_file="$3"
  if is_running "${pid_file}"; then
    return 0
  fi
  echo "[${name}] exited before becoming ready"
  print_log_excerpt "${log_file}"
  return 1
}

http_ready() {
  local url="$1"
  curl -fsS "${url}" >/dev/null 2>&1
}

wait_for_http() {
  local name="$1"
  local url="$2"
  local timeout_seconds="$3"
  local pid_file="${4:-}"
  local log_file="${5:-}"
  local waited=0

  echo "[${name}] waiting for ${url} ..."
  while (( waited < timeout_seconds )); do
    if http_ready "${url}"; then
      echo "[${name}] ready"
      return 0
    fi
    if [[ -n "${pid_file}" ]] && [[ -n "${log_file}" ]]; then
      assert_process_alive "${name}" "${pid_file}" "${log_file}" || return 1
    fi
    sleep 1
    waited=$((waited + 1))
  done

  echo "[${name}] timed out after ${timeout_seconds}s waiting for ${url}"
  if [[ -n "${pid_file}" ]] && [[ -n "${log_file}" ]]; then
    print_log_excerpt "${log_file}"
  fi
  return 1
}

wait_for_tcp() {
  local name="$1"
  local host="$2"
  local port="$3"
  local timeout_seconds="$4"
  local waited=0

  echo "[${name}] waiting for tcp://${host}:${port} ..."
  while (( waited < timeout_seconds )); do
    if (echo >"/dev/tcp/${host}/${port}") >/dev/null 2>&1; then
      echo "[${name}] ready"
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done

  echo "[${name}] timed out after ${timeout_seconds}s waiting for tcp://${host}:${port}"
  return 1
}

wait_for_postgres() {
  local container_id
  local waited=0

  container_id="$(cd "${ROOT_DIR}" && run_local_compose ps -q postgres 2>/dev/null || true)"
  if [[ -n "${container_id}" ]]; then
    echo "[postgres] waiting for container health ..."
    while (( waited < POSTGRES_WAIT_TIMEOUT )); do
      local status
      status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container_id}" 2>/dev/null || true)"
      if [[ "${status}" == "healthy" ]]; then
        echo "[postgres] ready"
        return 0
      fi
      if [[ "${status}" == "exited" || "${status}" == "dead" ]]; then
        echo "[postgres] container is not running (status=${status})"
        return 1
      fi
      sleep 1
      waited=$((waited + 1))
    done
    echo "[postgres] health check timed out after ${POSTGRES_WAIT_TIMEOUT}s"
    return 1
  fi

  wait_for_tcp "postgres" "${POSTGRES_HOST}" "${POSTGRES_PORT}" "${POSTGRES_WAIT_TIMEOUT}"
}

start_postgres() {
  echo "[postgres] starting..."
  (cd "${ROOT_DIR}" && run_local_compose up -d postgres)
}

start_embedding() {
  if is_running "${EMBED_PID_FILE}"; then
    echo "[embedding] already running (pid=$(cat "${EMBED_PID_FILE}"))"
    return
  fi
  echo "[embedding] starting on :8001..."
  (
    cd "${ROOT_DIR}/Qwen"
    nohup env \
      EMBEDDING_MODEL_ID="${EMBEDDING_MODEL}" \
      HF_HOME="${HF_CACHE_DIR}" \
      HF_ENDPOINT="${HF_ENDPOINT}" \
      HF_HUB_OFFLINE="${HF_HUB_OFFLINE}" \
      HF_LOCAL_FILES_ONLY="${HF_LOCAL_FILES_ONLY}" \
      HF_HUB_DISABLE_XET="${HF_HUB_DISABLE_XET}" \
      HTTP_PROXY="${HTTP_PROXY_VALUE}" \
      HTTPS_PROXY="${HTTPS_PROXY_VALUE}" \
      NO_PROXY="${NO_PROXY_VALUE}" \
      http_proxy="${HTTP_PROXY_VALUE}" \
      https_proxy="${HTTPS_PROXY_VALUE}" \
      no_proxy="${NO_PROXY_VALUE}" \
      DEVICE="${EMBEDDING_DEVICE}" \
      TORCH_DTYPE="${QWEN_TORCH_DTYPE}" \
      TORCH_NUM_THREADS="${QWEN_TORCH_NUM_THREADS}" \
      TORCH_NUM_INTEROP_THREADS="${QWEN_TORCH_NUM_INTEROP_THREADS}" \
      OMP_NUM_THREADS="${QWEN_TORCH_NUM_THREADS}" \
      MKL_NUM_THREADS="${QWEN_TORCH_NUM_THREADS}" \
      MAX_BATCH_SIZE="${EMBEDDING_BATCH_SIZE}" \
      MAX_CONCURRENT_REQUESTS="${EMBEDDING_MAX_CONCURRENT_REQUESTS}" \
      MAX_INPUT_LENGTH="${EMBEDDING_MAX_INPUT_LENGTH}" \
      IDLE_UNLOAD_SECONDS="${EMBEDDING_IDLE_UNLOAD_SECONDS}" \
      IDLE_CHECK_INTERVAL_SECONDS="${EMBEDDING_IDLE_CHECK_INTERVAL_SECONDS}" \
      PRELOAD_MODELS="${PRELOAD_MODELS}" \
      MALLOC_ARENA_MAX="${MALLOC_ARENA_MAX}" \
      TOKENIZERS_PARALLELISM="false" \
      HOST=0.0.0.0 \
      PORT=8001 \
      python3 embedding_service.py >"${EMBED_LOG}" 2>&1 &
    echo $! >"${EMBED_PID_FILE}"
  )
}

start_backend() {
  if is_running "${BACKEND_PID_FILE}"; then
    echo "[backend] already running (pid=$(cat "${BACKEND_PID_FILE}"))"
    return
  fi
  echo "[backend] starting on :8080..."
  (
    cd "${ROOT_DIR}/backend/cvect"
    nohup env \
      CVECT_DB_URL="${BACKEND_DB_URL}" \
      CVECT_DB_USERNAME="${BACKEND_DB_USERNAME}" \
      CVECT_DB_PASSWORD="${BACKEND_DB_PASSWORD}" \
      CVECT_SERVER_PORT="${BACKEND_PORT}" \
      CVECT_SECURITY_ENABLED="${SECURITY_ENABLED}" \
      CVECT_JWT_SECRET="${JWT_SECRET}" \
      CVECT_CACHE_ENABLED="${CACHE_ENABLED}" \
      CVECT_VECTOR_ENABLED="${VECTOR_ENABLED}" \
      CVECT_VECTOR_INGEST_WORKER_ENABLED="${VECTOR_INGEST_WORKER_ENABLED}" \
      CVECT_UPLOAD_WORKER_ENABLED="${UPLOAD_WORKER_ENABLED}" \
      CVECT_EMBEDDING_SERVICE_URL="${EMBEDDING_SERVICE_URL}" \
      CVECT_EMBEDDING_API_FORMAT="${EMBEDDING_API_FORMAT}" \
      CVECT_EMBEDDING_HEALTH_URL="${EMBEDDING_HEALTH_URL}" \
      CVECT_EMBEDDING_DEVICE="${EMBEDDING_DEVICE}" \
      CVECT_EMBEDDING_BATCH_SIZE="${EMBEDDING_BATCH_SIZE}" \
      CVECT_EMBEDDING_MAX_CONCURRENT_REQUESTS="${EMBEDDING_MAX_CONCURRENT_REQUESTS}" \
      CVECT_EMBEDDING_MAX_INPUT_LENGTH="${EMBEDDING_MAX_INPUT_LENGTH}" \
      CVECT_EMBEDDING_IDLE_UNLOAD_SECONDS="${EMBEDDING_IDLE_UNLOAD_SECONDS}" \
      CVECT_EMBEDDING_IDLE_CHECK_INTERVAL_SECONDS="${EMBEDDING_IDLE_CHECK_INTERVAL_SECONDS}" \
      CVECT_UPLOAD_MAX_INFLIGHT_ITEMS="${UPLOAD_MAX_INFLIGHT}" \
      CVECT_UPLOAD_MAX_FILES_PER_ZIP="${UPLOAD_MAX_FILES_PER_ZIP}" \
      ./mvnw -Dmaven.test.skip=true spring-boot:run >"${BACKEND_LOG}" 2>&1 &
    echo $! >"${BACKEND_PID_FILE}"
  )
}

start_frontend() {
  if is_running "${FRONTEND_PID_FILE}"; then
    echo "[frontend] already running (pid=$(cat "${FRONTEND_PID_FILE}"))"
    return
  fi
  echo "[frontend] starting on :5173..."
  (
    cd "${ROOT_DIR}/frontend"
    if [[ ! -d node_modules ]]; then
      npm install
    fi
    nohup env \
      VITE_API_PROXY_TARGET="http://localhost:${BACKEND_PORT}" \
      npm run dev -- --host >"${FRONTEND_LOG}" 2>&1 &
    echo $! >"${FRONTEND_PID_FILE}"
  )
}

stop_one() {
  local name="$1"
  local pid_file="$2"
  if is_running "${pid_file}"; then
    local pid
    pid="$(cat "${pid_file}")"
    echo "[${name}] stopping pid=${pid}..."
    kill "${pid}" >/dev/null 2>&1 || true
    sleep 1
    if kill -0 "${pid}" >/dev/null 2>&1; then
      kill -9 "${pid}" >/dev/null 2>&1 || true
    fi
    rm -f "${pid_file}"
  else
    rm -f "${pid_file}"
    echo "[${name}] not running"
  fi
}

kill_by_pattern() {
  local name="$1"
  local pattern="$2"
  local pids
  pids="$(pgrep -f "${pattern}" || true)"
  if [[ -z "${pids}" ]]; then
    return
  fi
  echo "[${name}] stopping stray pids: ${pids}"
  kill ${pids} >/dev/null 2>&1 || true
  sleep 1
  local still
  still="$(pgrep -f "${pattern}" || true)"
  if [[ -n "${still}" ]]; then
    kill -9 ${still} >/dev/null 2>&1 || true
  fi
}

status_one() {
  local name="$1"
  local pid_file="$2"
  if is_running "${pid_file}"; then
    echo "[${name}] running (pid=$(cat "${pid_file}"))"
  else
    echo "[${name}] stopped"
  fi
}

start_all() {
  start_postgres
  wait_for_postgres
  start_embedding
  wait_for_http "embedding" "${EMBEDDING_HEALTH_URL}" "${EMBED_WAIT_TIMEOUT}" "${EMBED_PID_FILE}" "${EMBED_LOG}"
  start_backend
  start_frontend
  wait_for_http "backend" "${BACKEND_HEALTH_URL}" "${BACKEND_WAIT_TIMEOUT}" "${BACKEND_PID_FILE}" "${BACKEND_LOG}"
  wait_for_http "frontend" "${FRONTEND_URL}" "${FRONTEND_WAIT_TIMEOUT}" "${FRONTEND_PID_FILE}" "${FRONTEND_LOG}"
  echo ""
  echo "All local services are ready. Logs:"
  echo "  embedding: ${EMBED_LOG}"
  echo "  backend:   ${BACKEND_LOG}"
  echo "  frontend:  ${FRONTEND_LOG}"
  echo ""
  echo "Upload limits:"
  echo "  CVECT_UPLOAD_MAX_INFLIGHT_ITEMS=${UPLOAD_MAX_INFLIGHT}"
  echo "  CVECT_UPLOAD_MAX_FILES_PER_ZIP=${UPLOAD_MAX_FILES_PER_ZIP}"
  echo ""
  echo "URLs:"
  echo "  frontend:  ${FRONTEND_URL}"
  echo "  backend:   ${BACKEND_HEALTH_URL%/api/resumes/health}"
  echo "  embedding: ${EMBEDDING_HEALTH_URL}"
}

stop_all() {
  stop_one "frontend" "${FRONTEND_PID_FILE}"
  stop_one "backend" "${BACKEND_PID_FILE}"
  stop_one "embedding" "${EMBED_PID_FILE}"
  kill_by_pattern "frontend" "${FRONTEND_PATTERN}"
  kill_by_pattern "backend" "${BACKEND_PATTERN_1}"
  kill_by_pattern "backend" "${BACKEND_PATTERN_2}"
  kill_by_pattern "embedding" "${EMBED_PATTERN}"
  kill_by_pattern "embedding" "${EMBED_PATTERN_2}"
}

status_all() {
  status_one "frontend" "${FRONTEND_PID_FILE}"
  status_one "backend" "${BACKEND_PID_FILE}"
  status_one "embedding" "${EMBED_PID_FILE}"
}

cmd="${1:-start}"
case "${cmd}" in
  start)
    start_all
    ;;
  stop)
    stop_all
    ;;
  restart)
    stop_all
    start_all
    ;;
  status)
    status_all
    ;;
  *)
    echo "Usage: scripts/local-run.sh {start|stop|restart|status}"
    exit 1
    ;;
esac

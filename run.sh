#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="${ROOT_DIR}/.run"
LOG_DIR="${RUN_DIR}/logs"
PID_DIR="${RUN_DIR}/pids"

mkdir -p "${LOG_DIR}" "${PID_DIR}"

BACKEND_PID_FILE="${PID_DIR}/backend.pid"
FRONTEND_PID_FILE="${PID_DIR}/frontend.pid"
EMBED_PID_FILE="${PID_DIR}/embedding.pid"

BACKEND_LOG="${LOG_DIR}/backend.log"
FRONTEND_LOG="${LOG_DIR}/frontend.log"
EMBED_LOG="${LOG_DIR}/embedding.log"
UPLOAD_MAX_INFLIGHT="${CVECT_UPLOAD_MAX_INFLIGHT_ITEMS:-2000}"
UPLOAD_MAX_FILES_PER_ZIP="${CVECT_UPLOAD_MAX_FILES_PER_ZIP:-2000}"
BACKEND_PATTERN_1="${ROOT_DIR}/backend/cvect"
BACKEND_PATTERN_2="com.walden.cvect.CvectApplication"
FRONTEND_PATTERN="${ROOT_DIR}/frontend/node_modules/.bin/vite --host"
EMBED_PATTERN="${ROOT_DIR}/Qwen/embedding_service.py"
EMBED_PATTERN_2="python3 embedding_service.py"

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

start_postgres() {
  echo "[postgres] starting..."
  (cd "${ROOT_DIR}" && docker compose up -d postgres)
}

start_embedding() {
  if is_running "${EMBED_PID_FILE}"; then
    echo "[embedding] already running (pid=$(cat "${EMBED_PID_FILE}"))"
    return
  fi
  echo "[embedding] starting on :8001..."
  (
    cd "${ROOT_DIR}/Qwen"
    nohup env HOST=0.0.0.0 PORT=8001 PRELOAD_MODELS=false python3 embedding_service.py >"${EMBED_LOG}" 2>&1 &
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
    nohup npm run dev -- --host >"${FRONTEND_LOG}" 2>&1 &
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
  start_embedding
  start_backend
  start_frontend
  echo ""
  echo "Services are starting. Logs:"
  echo "  embedding: ${EMBED_LOG}"
  echo "  backend:   ${BACKEND_LOG}"
  echo "  frontend:  ${FRONTEND_LOG}"
  echo ""
  echo "Upload limits:"
  echo "  CVECT_UPLOAD_MAX_INFLIGHT_ITEMS=${UPLOAD_MAX_INFLIGHT}"
  echo "  CVECT_UPLOAD_MAX_FILES_PER_ZIP=${UPLOAD_MAX_FILES_PER_ZIP}"
  echo ""
  echo "URLs:"
  echo "  frontend:  http://localhost:5173"
  echo "  backend:   http://localhost:8080"
  echo "  embedding: http://localhost:8001/health"
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
    echo "Usage: ./run.sh {start|stop|restart|status}"
    exit 1
    ;;
esac

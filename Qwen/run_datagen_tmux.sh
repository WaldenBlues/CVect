#!/usr/bin/env bash
set -euo pipefail

SESSION_NAME="${SESSION_NAME:-datagen_safe}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_BASE="${OUT_BASE:-/tmp/cvect_datagen}"
OUT_DIR="${OUT_DIR:-$OUT_BASE/generated_resumes_1000}"
WORK_DIR="${WORK_DIR:-$OUT_BASE/.datagen_work}"
LOG_DIR="${LOG_DIR:-$OUT_BASE/logs}"
LOG_FILE="$LOG_DIR/${SESSION_NAME}.log"
PID_FILE="$LOG_DIR/${SESSION_NAME}.pid"

mkdir -p "$LOG_DIR"

usage() {
  cat <<'EOF'
Usage:
  run_datagen_tmux.sh start [extra args for run_datagen_long.py]
  run_datagen_tmux.sh attach
  run_datagen_tmux.sh status
  run_datagen_tmux.sh logs
  run_datagen_tmux.sh stop

Examples:
  export DEEPSEEK_API_KEY='...'
  ./run_datagen_tmux.sh start --total 1000 --batch-size 25
  ./run_datagen_tmux.sh attach
  ./run_datagen_tmux.sh logs
EOF
}

require_tmux() {
  if ! command -v tmux >/dev/null 2>&1; then
    echo "tmux is not installed." >&2
    exit 1
  fi
}

session_exists() {
  tmux has-session -t "$SESSION_NAME" 2>/dev/null
}

start_session() {
  require_tmux
  if session_exists; then
    echo "Session '$SESSION_NAME' already exists."
    echo "Attach with: tmux attach -t $SESSION_NAME"
    exit 0
  fi
  if [[ -z "${DEEPSEEK_API_KEY:-}" ]]; then
    echo "DEEPSEEK_API_KEY is not set." >&2
    exit 1
  fi

  local extra_args=("$@")
  local runner_prefix=""
  if command -v ionice >/dev/null 2>&1; then
    runner_prefix="$runner_prefix ionice -c3"
  fi
  if command -v nice >/dev/null 2>&1; then
    runner_prefix="$runner_prefix nice -n 15"
  fi

  local cmd
  cmd="cd '$SCRIPT_DIR' && \
$runner_prefix python3 run_datagen_long.py \
  --total 1000 \
  --batch-size 25 \
  --provider deepseek \
  --model deepseek-chat \
  --out-dir '$OUT_DIR' \
  --work-dir '$WORK_DIR' \
  --progress-every 20 \
  --passthrough=--llm-fallback-synthetic \
  --passthrough=--resource-profile=interactive \
  --passthrough=--export-workers=1 \
  --passthrough=--yield-seconds=0.02 \
  --passthrough=--cpu-nice=10 \
  --passthrough=--progress-every=10 \
  ${extra_args[*]:-} \
  2>&1 | tee -a '$LOG_FILE'; \
echo \"[exit-code] \$?\" | tee -a '$LOG_FILE'"

  tmux new-session -d -s "$SESSION_NAME" -e "DEEPSEEK_API_KEY=$DEEPSEEK_API_KEY" "$cmd"
  tmux list-panes -t "$SESSION_NAME" -F '#{pane_pid}' > "$PID_FILE" || true

  echo "Started tmux session: $SESSION_NAME"
  echo "Attach: tmux attach -t $SESSION_NAME"
  echo "Logs:   tail -f $LOG_FILE"
  echo "Out:    $OUT_DIR"
}

attach_session() {
  require_tmux
  tmux attach -t "$SESSION_NAME"
}

status_session() {
  require_tmux
  if session_exists; then
    echo "Session '$SESSION_NAME' is running."
    tmux list-sessions | grep "^${SESSION_NAME}:" || tmux list-sessions
  else
    echo "Session '$SESSION_NAME' is not running."
  fi
  if [[ -f "$LOG_FILE" ]]; then
    echo "Log file: $LOG_FILE"
  fi
}

logs_session() {
  if [[ -f "$LOG_FILE" ]]; then
    tail -n 200 "$LOG_FILE"
  else
    echo "No log file yet: $LOG_FILE"
  fi
}

stop_session() {
  require_tmux
  if session_exists; then
    tmux kill-session -t "$SESSION_NAME"
    echo "Stopped session '$SESSION_NAME'."
  else
    echo "Session '$SESSION_NAME' is not running."
  fi
}

main() {
  local action="${1:-}"
  shift || true
  case "$action" in
    start) start_session "$@" ;;
    attach) attach_session ;;
    status) status_session ;;
    logs) logs_session ;;
    stop) stop_session ;;
    ""|-h|--help|help) usage ;;
    *)
      echo "Unknown action: $action" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"

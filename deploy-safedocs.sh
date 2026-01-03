#!/usr/bin/env bash

set -euo pipefail

APP_DIR="/opt/apps/safedocs-service"

usage() {
  cat <<'EOF'
SafeDocs helper
1) restart           - rebuild image and restart stack (no volume reset)
2) reset-db          - stop stack, remove volumes, rebuild and start fresh
3) pull-and-restart  - git pull --ff-only, rebuild, restart stack
4) stop              - docker compose down
5) logs              - docker logs -f safedocs_app
EOF
}

restart() {
  echo "==> Rebuilding and restarting stack (no volume reset)"
  docker compose up -d --build
}

reset_db() {
  echo "==> WARNING: resetting DB volumes"
  docker compose down -v
  docker compose up -d --build
}

pull_and_restart() {
  echo "==> Updating git repository (git pull --ff-only)"
  git pull --ff-only
  restart
}

stop_stack() {
  echo "==> Stopping stack"
  docker compose down
}

show_logs() {
  echo "==> Tailing safedocs_app logs (Ctrl+C to stop)"
  docker logs -f --tail 200 safedocs_app
}

main() {
  if [ ! -d "${APP_DIR}" ]; then
    echo "Error: ${APP_DIR} does not exist." >&2
    exit 1
  fi
  cd "${APP_DIR}"

  local choice="${1:-}"
  if [ -z "${choice}" ]; then
    usage
    read -rp "Select option [1-5]: " choice
  fi

  case "${choice}" in
    1|restart) restart ;;
    2|reset-db) reset_db ;;
    3|pull|pull-and-restart|git-pull) pull_and_restart ;;
    4|stop) stop_stack ;;
    5|logs) show_logs ;;
    *) usage; exit 1 ;;
  esac
}

main "$@"

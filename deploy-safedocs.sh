#!/usr/bin/env bash

set -euo pipefail

APP_DIR="/opt/apps/safedocs-service"

echo "==> Deploying SafeDocs from ${APP_DIR}"

if [ ! -d "${APP_DIR}" ]; then
  echo "Error: ${APP_DIR} does not exist." >&2
  exit 1
fi

cd "${APP_DIR}"

echo "==> Updating git repository (git pull --ff-only)"
git pull --ff-only

echo "==> Rebuilding and restarting Docker stack (docker compose up -d --build)"
docker compose up -d --build

echo "==> SafeDocs deploy complete."

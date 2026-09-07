#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"

legacy='org.testcontainers.containers.PostgreSQLContainer'

matches=$(grep -RIn --include='*.java' --exclude-dir=target --exclude-dir=.validation "$legacy" . || true)
if [[ -n "$matches" ]]; then
  echo "[deprecations] FAIL — deprecated Testcontainers PostgreSQL API detected"
  printf '%s\n' "$matches"
  echo "[deprecations] Use org.testcontainers.postgresql.PostgreSQLContainer instead."
  exit 1
fi

echo "[deprecations] PASS — no legacy Testcontainers PostgreSQL API"

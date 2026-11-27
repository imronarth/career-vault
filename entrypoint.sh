#!/bin/sh
set -eu

DB_HOST="${DB_HOST:-db}"
DB_PORT="${DB_PORT:-5432}"
TIMEOUT="${DB_WAIT_TIMEOUT:-60}"

echo "Waiting for ${DB_HOST}:${DB_PORT} (timeout: ${TIMEOUT}s)..."
elapsed=0
while ! nc -z "$DB_HOST" "$DB_PORT" >/dev/null 2>&1; do
  if [ "$elapsed" -ge "$TIMEOUT" ]; then
    echo "ERROR: Timed out waiting for ${DB_HOST}:${DB_PORT} after ${TIMEOUT}s" >&2
    exit 1
  fi
  sleep 1
  elapsed=$((elapsed + 1))
done

echo "${DB_HOST}:${DB_PORT} is reachable. Starting Career Vault..."
exec java -XX:MaxRAMPercentage=75.0 -jar /app/app.jar

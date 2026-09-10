#!/usr/bin/env bash
#
# Reproduces the README/docs/throttlr_demo.gif recording: health check, one normal
# request through nginx (proving load balancing via servedByInstance), one client key
# bursting past its quota until the shared Redis bucket trips 429, then the live bucket
# status confirming it.
#
# Usage: ./scripts/demo.sh [base_url]
# Requires the stack already running, e.g. `docker compose up --build -d`.
set -uo pipefail

BASE_URL="${1:-http://localhost:8080}"
RESP_FILE="$(mktemp)"
trap 'rm -f "$RESP_FILE"' EXIT

clear 2>/dev/null || true
echo "=== throttlr — distributed rate limiter ==="
echo "3 Spring Boot instances behind nginx, one shared Redis bucket per client"
echo
sleep 2

echo "\$ curl $BASE_URL/actuator/health"
curl -s "$BASE_URL/actuator/health"
echo
echo
sleep 2

echo "\$ curl -H 'X-API-Key: demo' $BASE_URL/api/v1/resource"
curl -s -H "X-API-Key: demo" "$BASE_URL/api/v1/resource"
echo
echo
sleep 2

echo "--- bursting one client key past its quota, through nginx ---"
echo
sleep 1
CLIENT="demo-burst-$RANDOM"
for i in $(seq 1 90); do
  status=$(curl -s -o "$RESP_FILE" -w "%{http_code}" -H "X-API-Key: $CLIENT" "$BASE_URL/api/v1/resource")
  printf "%s " "$status"
  sleep 0.02
  if [ "$status" == "429" ]; then
    break
  fi
done
echo
echo
echo "429 response body:"
cat "$RESP_FILE"
echo
echo
sleep 2.5

echo "\$ curl '$BASE_URL/api/v1/limiter/status?clientKey=$CLIENT'"
curl -s "$BASE_URL/api/v1/limiter/status?clientKey=$CLIENT"
echo

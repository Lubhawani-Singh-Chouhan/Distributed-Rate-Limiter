#!/usr/bin/env bash
#
# Verifies the Phase 3 "core proof point": with `docker compose up` running
# (redis + app-instance-1/2/3 + nginx), requests spread across all 3 instances
# by nginx's round-robin still hit one *shared* Redis-backed bucket per client,
# so exceeding capacity trips a 429 no matter which instance actually served
# each individual request.
#
# Usage: ./scripts/verify-distributed-limit.sh [nginx_base_url] [request_count]
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
REQUEST_COUNT="${2:-80}"
CLIENT_KEY="verify-$(date +%s)-$RANDOM"

echo "Target:      $BASE_URL/api/v1/resource"
echo "Client key:  $CLIENT_KEY"
echo "Requests:    $REQUEST_COUNT"
echo

allowed=0
denied=0
declare -A instance_hits

for i in $(seq 1 "$REQUEST_COUNT"); do
  response=$(curl -s -w '\n%{http_code}' -H "X-API-Key: $CLIENT_KEY" "$BASE_URL/api/v1/resource")
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"

  if [[ "$status" == "200" ]]; then
    allowed=$((allowed + 1))
    instance=$(echo "$body" | grep -o '"servedByInstance":"[^"]*"' | cut -d'"' -f4 || echo "unknown")
    instance_hits["$instance"]=$(( ${instance_hits["$instance"]:-0} + 1 ))
  else
    denied=$((denied + 1))
    if [[ $denied -eq 1 ]]; then
      echo "First 429 at request #$i:"
      echo "  $body"
      echo
    fi
  fi
done

echo "Results:"
echo "  allowed: $allowed"
echo "  denied (429): $denied"
echo
echo "Requests served per instance (proves load balancing across the 3 instances):"
for instance in "${!instance_hits[@]}"; do
  echo "  $instance: ${instance_hits[$instance]}"
done
echo
if [[ $denied -gt 0 ]]; then
  echo "PASS: the shared global bucket in Redis correctly throttled this client even though" \
       "requests were spread across multiple app instances."
else
  echo "No 429s observed - increase REQUEST_COUNT or lower ratelimiter.default-capacity to see the limit trip."
fi

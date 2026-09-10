#!/usr/bin/env bash
#
# Differentiating proof: the shared Redis bucket survives an instance death.
#
# A naive in-memory limiter (or in-memory + sticky sessions) gets this wrong:
# killing an instance either drops its local counters (clients get a fresh
# quota on the next hop) or pins a client to a dead node. Here, instance-2 is
# killed mid-load; nginx fails over to 1 and 3; the *same* Redis hash still
# exhausts at ~capacity, not 2x.
#
# Requires the Compose stack (redis + 3 apps + nginx). Restarts the killed
# container at the end so the stack is left usable.
#
# Usage: ./scripts/verify-failover-quota.sh [nginx_base_url] [kill_after] [total]
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
KILL_AFTER="${2:-20}"
TOTAL="${3:-80}"
INSTANCE_TO_KILL="${INSTANCE_TO_KILL:-app-instance-2}"
CAPACITY="${CAPACITY:-50}"
REFILL="${REFILL:-10}"
CLIENT_KEY="failover-$(date +%s)-${RANDOM}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required (to kill ${INSTANCE_TO_KILL})." >&2
  exit 1
fi
if ! docker inspect "$INSTANCE_TO_KILL" >/dev/null 2>&1; then
  echo "${INSTANCE_TO_KILL} is not running. Start the stack first:" >&2
  echo "  docker compose up --build" >&2
  exit 1
fi

allowed=0
denied=0
proxy_errors=0
killed=0
start_epoch=$(date +%s)
declare -A hits_before
declare -A hits_after

hit() {
  local response status body instance
  # Do not use curl -f: 429 and 502 are both expected at different phases.
  response=$(curl -s -S --max-time 3 -w '\n%{http_code}' -H "X-API-Key: ${CLIENT_KEY}" \
    "${BASE_URL}/api/v1/resource" || true)
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  if [[ "$status" == "200" ]]; then
    allowed=$((allowed + 1))
    instance=$(echo "$body" | grep -o '"servedByInstance":"[^"]*"' | cut -d'"' -f4 || echo "unknown")
    if [[ "$killed" -eq 0 ]]; then
      hits_before["$instance"]=$(( ${hits_before["$instance"]:-0} + 1 ))
    else
      hits_after["$instance"]=$(( ${hits_after["$instance"]:-0} + 1 ))
    fi
  elif [[ "$status" == "429" ]]; then
    denied=$((denied + 1))
  else
    proxy_errors=$((proxy_errors + 1))
  fi
}

echo "Target:           ${BASE_URL}/api/v1/resource"
echo "Client key:       ${CLIENT_KEY}"
echo "Kill after:       ${KILL_AFTER} requests  (${INSTANCE_TO_KILL})"
echo "Total requests:   ${TOTAL}"
echo "Expected cap:     ${CAPACITY} burst + ${REFILL}/s refill"
echo

for i in $(seq 1 "$TOTAL"); do
  if [[ "$i" -eq $((KILL_AFTER + 1)) ]]; then
    echo "--- docker kill ${INSTANCE_TO_KILL} (after ${KILL_AFTER} requests) ---"
    docker kill "$INSTANCE_TO_KILL" >/dev/null
    killed=1
    echo
  fi
  hit
done

elapsed=$(( $(date +%s) - start_epoch ))
if [[ "$elapsed" -lt 1 ]]; then
  elapsed=1
fi
max_allowed=$(( CAPACITY + REFILL * elapsed + 5 ))

echo
echo "Results (${elapsed}s elapsed):"
echo "  allowed (200):     ${allowed}"
echo "  denied  (429):     ${denied}"
echo "  proxy errors:      ${proxy_errors}  (502/timeout after the kill are expected)"
echo "  hard ceiling:      ${max_allowed}  (${CAPACITY} + ${REFILL}/s × ${elapsed}s + slack)"
echo
echo "200s before kill:"
if [[ ${#hits_before[@]} -eq 0 ]]; then
  echo "  (none)"
else
  for instance in $(printf '%s\n' "${!hits_before[@]}" | sort); do
    echo "  ${instance}: ${hits_before[$instance]}"
  done
fi
echo "200s after kill:"
if [[ ${#hits_after[@]} -eq 0 ]]; then
  echo "  (none)"
else
  for instance in $(printf '%s\n' "${!hits_after[@]}" | sort); do
    echo "  ${instance}: ${hits_after[$instance]}"
  done
fi
echo

pass=1
if [[ "$denied" -eq 0 ]]; then
  echo "FAIL: never hit 429 — quota did not exhaust."
  pass=0
fi
if [[ "$allowed" -gt "$max_allowed" ]]; then
  echo "FAIL: allowed ${allowed} > ${max_allowed}. A per-instance in-memory bucket"
  echo "      would look like this after a crash (fresh quota on remaining nodes)."
  pass=0
fi
after_killed="${hits_after[$INSTANCE_TO_KILL]:-0}"
if [[ "$after_killed" -gt 0 ]]; then
  echo "FAIL: ${INSTANCE_TO_KILL} still served ${after_killed} request(s) after docker kill."
  pass=0
fi
survived_200=0
for instance in "${!hits_after[@]}"; do
  survived_200=$((survived_200 + hits_after["$instance"]))
done
if [[ "$survived_200" -eq 0 && "$denied" -eq 0 ]]; then
  echo "FAIL: no successful traffic after the kill (stack may be down)."
  pass=0
fi

echo "Restarting ${INSTANCE_TO_KILL}..."
docker start "$INSTANCE_TO_KILL" >/dev/null || true

if [[ "$pass" -eq 1 ]]; then
  echo
  echo "PASS: killing ${INSTANCE_TO_KILL} did not reset the bucket. Remaining instances"
  echo "      kept consuming the same Redis hash; the client still exhausted at ~capacity,"
  echo "      not N× capacity."
  exit 0
fi
exit 1

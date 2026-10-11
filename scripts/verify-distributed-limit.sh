#!/usr/bin/env bash
#
# Shared-quota proof for the Compose topology (nginx + 3 app instances + Redis).
#
# One client key is sprayed across instances. The number of HTTP 200s in the
# measured window must stay within a single token bucket:
#
#   allowed <= capacity + refill_per_second * elapsed_seconds
#
# Three independent in-memory buckets would allow about 3x capacity in the same
# window. Defaults match application.yml (capacity 50, refill 10/s). Override
# them when the running process uses different settings:
#
#   CAPACITY=50 REFILL=10 ./scripts/verify-distributed-limit.sh http://localhost:8080 120
#
# Exit 0 only when every response is 200 or 429 and the ceiling holds.
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
REQUEST_COUNT="${2:-120}"
CAPACITY="${CAPACITY:-50}"
REFILL="${REFILL:-10}"
PARALLEL="${PARALLEL:-30}"
CLIENT_KEY="verify-$(date +%s)-$RANDOM"
URL="${BASE_URL}/api/v1/resource"

now_ms() {
  if command -v python3 >/dev/null 2>&1; then
    python3 -c 'import time; print(int(time.time() * 1000))'
    return
  fi
  if command -v python >/dev/null 2>&1; then
    python -c 'import time; print(int(time.time() * 1000))'
    return
  fi
  local ms
  ms=$(date +%s%3N 2>/dev/null || true)
  if [[ "$ms" =~ ^[0-9]{13,}$ ]]; then
    echo "$ms"
    return
  fi
  echo $(( $(date +%s) * 1000 ))
}

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

echo "Target:       $URL"
echo "Client key:   $CLIENT_KEY"
echo "Requests:     $REQUEST_COUNT  (parallelism $PARALLEL)"
echo "Bucket:       capacity=$CAPACITY  refill=$REFILL/s"
echo

start_ms=$(now_ms)
for i in $(seq 1 "$REQUEST_COUNT"); do
  (
    code=$(curl -sS --max-time 5 -o "$tmpdir/$i.body" -w '%{http_code}' \
      -H "X-API-Key: $CLIENT_KEY" "$URL" || true)
    if [[ -z "$code" ]]; then
      code="000"
    fi
    printf '%s' "$code" > "$tmpdir/$i.code"
  ) &
  if (( i % PARALLEL == 0 )); then
    wait
  fi
done
wait
end_ms=$(now_ms)

allowed=0
denied=0
errors=0
declare -A instance_hits

for i in $(seq 1 "$REQUEST_COUNT"); do
  status=$(cat "$tmpdir/$i.code" 2>/dev/null || echo "000")
  if [[ "$status" == "200" ]]; then
    allowed=$((allowed + 1))
    instance=$(grep -o '"servedByInstance":"[^"]*"' "$tmpdir/$i.body" 2>/dev/null | head -n 1 | cut -d'"' -f4 || true)
    instance=${instance:-unknown}
    instance_hits["$instance"]=$(( ${instance_hits["$instance"]:-0} + 1 ))
  elif [[ "$status" == "429" ]]; then
    denied=$((denied + 1))
  else
    errors=$((errors + 1))
  fi
done

elapsed_ms=$(( end_ms - start_ms ))
if (( elapsed_ms < 1 )); then
  elapsed_ms=1
fi
# floor(refill * seconds). Wall time covers the whole burst, so it is an upper
# bound on how long Redis TIME was advancing. +1 absorbs a fractional token
# that lands on the measurement boundary.
earned=$(( REFILL * elapsed_ms / 1000 ))
max_allowed=$(( CAPACITY + earned + 1 ))

echo "Results (${elapsed_ms} ms):"
echo "  allowed (200):  $allowed"
echo "  denied (429):   $denied"
echo "  other:          $errors"
echo "  ceiling:        $max_allowed  (= $CAPACITY + $REFILL/s * ${elapsed_ms}ms + 1)"
echo
echo "200s per instance:"
if [[ ${#instance_hits[@]} -eq 0 ]]; then
  echo "  (none)"
else
  for instance in $(printf '%s\n' "${!instance_hits[@]}" | sort); do
    echo "  $instance: ${instance_hits[$instance]}"
  done
fi
echo

pass=1
if (( errors > 0 )); then
  echo "FAIL: $errors response(s) were not 200 or 429. The ceiling is only meaningful when the stack answered."
  pass=0
fi
if (( allowed > max_allowed )); then
  echo "FAIL: allowed $allowed > $max_allowed."
  echo "      One shared bucket cannot grant more than capacity + refill*time."
  echo "      N independent buckets (one per app instance) can."
  pass=0
fi
if (( ${#instance_hits[@]} < 2 && allowed >= 2 )); then
  echo "WARN: 200s came from fewer than 2 instances. Point this at nginx (:8080), not a single app port."
fi

if (( pass == 1 )); then
  echo "PASS: allowed $allowed <= $max_allowed over ${elapsed_ms}ms for one client."
  exit 0
fi
exit 1

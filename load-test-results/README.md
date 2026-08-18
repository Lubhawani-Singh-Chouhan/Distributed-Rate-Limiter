# Load test results

Real, measured runs — not estimates. Gatling HTML reports live under `gatling/<simulation>-<timestamp>/`
(open `index.html`). 429s are treated as passing HTTP checks; only 5xx / connection failures
count as Gatling `KO`.

## Headline run — 1,000 req/s for 60s

**Report:** [`gatling/sustainedthroughputsimulation-20260818040033152/index.html`](gatling/sustainedthroughputsimulation-20260818040033152/index.html)

| | |
|---|---|
| Date | 2026-08-18 04:00:36 GMT (duration 1m 19s) |
| Simulation | `SustainedThroughputSimulation` |
| Command | `mvn gatling:test -Dgatling.simulationClass=simulations.SustainedThroughputSimulation -Dbase.url=http://127.0.0.1:8080 -Dtarget.rps=1000 -Dramp.seconds=20 -Dsustain.seconds=60 -Dmax.mean.latency.ms=50` |
| Target | 1 Spring Boot instance (`mvnw spring-boot:run`) + Redis 7 in Docker Desktop, Windows |
| Workload | Open model, 200 rotating `X-API-Key` tenants, `GET /api/v1/resource` |
| Offered | 20s ramp 1→1000 rps, then 60s at 1000 rps |
| Completed | **70,010 / 70,010** (exactly the offered load) |
| Errors | **0** (0% KO) |
| Full-run mean throughput | 875.12 req/s (ramp included) |
| Sustain (~last 60s) | **~1,001 req/s** (60,061 requests after the ramp) |
| Latency | mean **9 ms**, p50 7 ms, p75 9 ms, p95 **19 ms**, p99 **42 ms**, max 274 ms |
| Assertions | failed-events ≤ 1% **OK**; mean RT ≤ 50 ms **OK** |

This is the number to quote for a “~1k rps sustained” claim. It is **not** “one client is
allowed 1,000 req/s”: `default-refill-rate` is 10 tokens/s per key (burst 50). Many keys are
how 1k limiter **decisions**/sec stay inside the buckets.

It is also **not** the 3-instance nginx topology. Re-run the same command against
`http://localhost:8080` after `docker compose up --build` for that measurement.

## Earlier sandbox runs (same-host Redis, no Docker)

These were captured against a **single app instance** talking to **Redis on the same host**
(no Docker network hop, no nginx) while the original sandbox had no Docker daemon:

| Scenario | Command | Throughput | Mean | p95 | p99 | Errors |
|---|---|---|---|---|---|---|
| A' — Sustained ~500 rps | `-Dtarget.rps=500 -Dramp.seconds=10 -Dsustain.seconds=30` | 437.6 req/s mean over ramp+sustain | 1 ms | 1 ms | 2 ms | 0% |
| B — Burst past capacity (50) | 300 sequential requests, one client | 63 allowed / 237 denied, first 429 at #53 | n/a | n/a | n/a | 0% |
| C — Latency under concurrency | `-Dconcurrent.users=30 -Dduration.seconds=8` | 33,128 req/s (closed model) | 1 ms | 2 ms | 4 ms | 0% |

**Scenario B detail:** `default-capacity=50`, `default-refill-rate=10`, 300 back-to-back
requests in 1.38s. 63 allowed (50 burst + ~14 refill) / 237 × 429. First rejection at
request #53 — the limiter enforces the configured threshold precisely, not “roughly.”

Same-host Redis explains the 1 ms band vs the 9 ms mean on the Docker-Redis 1k run. The Lua
round-trip is still the dominant cost; Docker Desktop adds a host↔container hop.

Committed HTML for those older Gatling runs:

- `gatling/burstsimulation-20260811193555659/`
- `gatling/sustainedthroughputsimulation-20260811193702974/`
- `gatling/latencyunderconcurrencysimulation-20260811193817553/`

## Raw reports

| Folder | What it is |
|---|---|
| [`gatling/sustainedthroughputsimulation-20260818040033152/`](gatling/sustainedthroughputsimulation-20260818040033152/index.html) | **Headline 1k rps / 60s sustain** (Scenario A) |
| `gatling/burstsimulation-*/` | Burst / correctness (200 and 429 both “pass” in Gatling; use a sequential loop for the exact 200/429 split) |
| `gatling/sustainedthroughputsimulation-*/` | Sustained throughput (open model) |
| `gatling/latencyunderconcurrencysimulation-*/` | Closed-model concurrency |

## Reproducing

```bash
docker compose up -d redis
./mvnw spring-boot:run

# Headline 1k / 60s run
./mvnw gatling:test -Dgatling.simulationClass=simulations.SustainedThroughputSimulation \
    -Dbase.url=http://127.0.0.1:8080 -Dtarget.rps=1000 -Dramp.seconds=20 -Dsustain.seconds=60 \
    -Dmax.mean.latency.ms=50

# Burst / closed-model
./mvnw gatling:test -Dgatling.simulationClass=simulations.BurstSimulation -Dbase.url=http://127.0.0.1:8080
./mvnw gatling:test -Dgatling.simulationClass=simulations.LatencyUnderConcurrencySimulation -Dbase.url=http://127.0.0.1:8080

# Shared limit across the 3-instance nginx topology
docker compose up --build
./scripts/verify-distributed-limit.sh http://localhost:8080 80
```

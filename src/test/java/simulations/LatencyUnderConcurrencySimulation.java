package simulations;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spec Scenario C - Latency under concurrency.
 *
 * Measures p50/p95/p99 of the rate-limit check itself. {@code /api/v1/resource} has no
 * real downstream work (it builds a tiny mock DTO), so its response time is dominated by:
 * servlet filter overhead + one Redis round trip running {@code token_bucket.lua} via
 * EVALSHA. That makes this endpoint a reasonable proxy for "cost of the rate-limit check,"
 * isolated from any real business logic latency per spec section 4.3.
 *
 * Uses a large pool of client keys (like Scenario A) so this measures steady-state
 * concurrent load rather than one hot key serializing through a single Redis key's
 * command queue.
 *
 * Run with:
 *   mvn gatling:test -Dgatling.simulationClass=simulations.LatencyUnderConcurrencySimulation \
 *       -Dbase.url=http://localhost:8080 -Dconcurrent.users=100
 */
public class LatencyUnderConcurrencySimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("base.url", "http://localhost:8080");
    private static final int CONCURRENT_USERS = Integer.getInteger("concurrent.users", 100);
    private static final int DURATION_SECONDS = Integer.getInteger("duration.seconds", 30);

    private final AtomicInteger keySequence = new AtomicInteger();

    private final Iterator<Map<String, Object>> clientKeyFeeder = new Iterator<>() {
        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public Map<String, Object> next() {
            String key = "latency-tenant-" + (keySequence.getAndIncrement() % 500);
            return Map.of("clientKey", key);
        }
    };

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(BASE_URL).acceptHeader("application/json").userAgentHeader("gatling-latency-concurrency");

    private final ScenarioBuilder scenario = scenario("Latency of the rate-limit check under concurrency")
            .feed(clientKeyFeeder)
            .during(Duration.ofSeconds(DURATION_SECONDS))
            .on(
                    exec(http("GET /api/v1/resource")
                            .get("/api/v1/resource")
                            .header("X-API-Key", "#{clientKey}")
                            .check(status().in(200, 429))));

    {
        setUp(scenario.injectOpen(atOnceUsers(CONCURRENT_USERS)))
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().percent().lte(1.0),
                        global().responseTime().percentile3().lte(10), // p95 <= 10ms target from spec
                        global().responseTime().percentile4().lte(25)); // p99 guardrail
    }
}

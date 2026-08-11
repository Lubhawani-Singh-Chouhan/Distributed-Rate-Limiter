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
 * Spec Scenario A - Sustained throughput.
 *
 * Ramps up to ~500 req/sec and sustains it for 60s against the rate-limited demo endpoint,
 * using many distinct client keys (a feeder) so the run exercises many independent token
 * buckets concurrently rather than repeatedly hammering a single one — this is what "500
 * req/sec across simulated tenants" actually needs to mean for the number to be meaningful.
 *
 * Run against a running instance (or the nginx-fronted 3-instance cluster) with:
 *   mvn gatling:test -Dgatling.simulationClass=simulations.SustainedThroughputSimulation \
 *       -Dbase.url=http://localhost:8080
 */
public class SustainedThroughputSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("base.url", "http://localhost:8080");
    private static final int TARGET_RPS = Integer.getInteger("target.rps", 500);
    private static final int RAMP_SECONDS = Integer.getInteger("ramp.seconds", 20);
    private static final int SUSTAIN_SECONDS = Integer.getInteger("sustain.seconds", 60);

    private final AtomicInteger keySequence = new AtomicInteger();

    private final Iterator<Map<String, Object>> clientKeyFeeder = new Iterator<>() {
        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public Map<String, Object> next() {
            // A large-but-bounded pool of tenants means every tenant's bucket gets hit
            // repeatedly (so refill behavior matters, not just a cold burst) while still
            // spreading load across ~200 independent buckets to simulate multiple tenants.
            String key = "loadtest-tenant-" + (keySequence.getAndIncrement() % 200);
            return Map.of("clientKey", key);
        }
    };

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(BASE_URL).acceptHeader("application/json").userAgentHeader("gatling-sustained-throughput");

    private final ScenarioBuilder scenario = scenario("Sustained throughput across many tenants")
            .feed(clientKeyFeeder)
            .exec(http("GET /api/v1/resource")
                    .get("/api/v1/resource")
                    .header("X-API-Key", "#{clientKey}")
                    .check(status().in(200, 429)));

    {
        setUp(
                scenario.injectOpen(
                        rampUsersPerSec(1).to(TARGET_RPS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(TARGET_RPS).during(Duration.ofSeconds(SUSTAIN_SECONDS))))
                .protocols(httpProtocol)
                .assertions(
                        // 429 is a valid business response, not a system failure - only 5xx / connection
                        // errors should count against the "error rate" per spec section 4.3.
                        global().failedRequests().percent().lte(1.0),
                        global().responseTime().mean().lte(50));
    }
}

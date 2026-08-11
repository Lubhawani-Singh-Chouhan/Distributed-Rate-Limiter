package simulations;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * Spec Scenario B - Burst test.
 *
 * A single client fires far more requests than its bucket capacity, all at once. This is a
 * correctness check, not a speed check: with {@code ratelimiter.default-capacity=50}
 * (the shipped default), we expect the number of 200s to land at/near 50 (a few extra can
 * legitimately be allowed if tokens refill mid-burst) and the remainder to be 429s —
 * proving the limiter enforces the configured threshold precisely under concurrency, not
 * "roughly."
 *
 * <p>The check below intentionally treats both 200 and 429 as a "passing" request, since
 * per spec section 4.3 a 429 is a valid business outcome, not a system failure. That means
 * the actual 200-vs-429 split for this run isn't visible via Gatling's built-in
 * successful/failed counters (those track check outcome, not HTTP status) — it's read
 * directly from the generated report / simulation.log instead; see
 * load-test-results/README.md for the exact grep command and the last recorded run.
 *
 * Run with:
 *   mvn gatling:test -Dgatling.simulationClass=simulations.BurstSimulation \
 *       -Dbase.url=http://localhost:8080 -Dbucket.capacity=50
 */
public class BurstSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("base.url", "http://localhost:8080");
    private static final int BURST_SIZE = Integer.getInteger("burst.size", 300);
    private static final String CLIENT_KEY = "burst-test-client-" + System.currentTimeMillis();

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(BASE_URL).acceptHeader("application/json").userAgentHeader("gatling-burst-test");

    private final ScenarioBuilder scenario = scenario("Single client burst past capacity")
            .exec(http("GET /api/v1/resource (burst)")
                    .get("/api/v1/resource")
                    .header("X-API-Key", CLIENT_KEY)
                    .check(status().in(200, 429)));

    {
        setUp(scenario.injectOpen(atOnceUsers(BURST_SIZE)))
                .protocols(httpProtocol)
                .assertions(
                        // Anything other than 200/429 (5xx, timeouts, connection errors) is a
                        // real failure, not an expected rejection.
                        global().failedRequests().count().is(0L));
    }
}

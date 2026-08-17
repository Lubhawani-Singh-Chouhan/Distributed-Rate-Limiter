package com.ratelimiter.distributed.util;

/**
 * Resolves a human-readable identifier for the current process, echoed back in demo
 * endpoint responses so that a curl loop through nginx can visibly show requests being
 * spread across app-instance-1/2/3 (Phase 3's core proof point) while the *shared* Redis
 * bucket still enforces one global limit.
 */
public final class InstanceIdentity {

    private static final String INSTANCE_ID =
            firstNonBlank(System.getenv("INSTANCE_ID"), System.getenv("HOSTNAME"), "local");

    private InstanceIdentity() {
    }

    public static String current() {
        return INSTANCE_ID;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "unknown";
    }
}

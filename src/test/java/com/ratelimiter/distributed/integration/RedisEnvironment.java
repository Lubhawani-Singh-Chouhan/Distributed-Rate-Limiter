package com.ratelimiter.distributed.integration;

import org.testcontainers.DockerClientFactory;

/**
 * Shared gate so Testcontainers tests skip instead of failing the suite when Docker
 * is not installed (local Windows without Docker Desktop). CI Ubuntu runners have Docker.
 */
public final class RedisEnvironment {

    private RedisEnvironment() {
    }

    public static boolean available() {
        if (System.getProperty("EXTERNAL_REDIS_HOST") != null) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }
}

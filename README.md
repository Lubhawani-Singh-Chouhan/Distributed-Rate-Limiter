# Distributed Rate Limiter

A distributed, Redis-backed **Token Bucket** rate limiter for Spring Boot — thread-safe and
horizontally scalable across multiple application instances via a single atomic Lua script.

> Status: scaffolding in place (Phase 0). See the project spec / commit history for the
> full build-out across Phases 1-5 (core algorithm, Spring integration, multi-instance
> Docker Compose topology, load testing, and polish).

## Tech stack

Java 21 · Spring Boot 3.3 · Redis 7 · Lua (Redis scripting) · Docker & Docker Compose ·
Maven · Testcontainers + JUnit 5 · Gatling.

## Project layout

```
com.ratelimiter.distributed
├── config/       # Redis config, bean definitions, ratelimiter.* properties
├── controller/   # Demo API + rate-limit status/debug endpoints
├── filter/       # Rate limiting servlet filter
├── service/      # Token bucket logic, key/rule resolution
├── model/        # DTOs, config models
├── exception/    # Custom exceptions + global handler
├── annotation/    # @RateLimit per-endpoint override
└── util/         # Lua script loader, key builders
```

## Running locally

```bash
docker compose up -d redis   # brings up just Redis for now
./mvnw spring-boot:run
```

More to come as later phases land.

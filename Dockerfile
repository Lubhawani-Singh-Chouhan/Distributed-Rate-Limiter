# --- Build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies separately from source so `docker build` doesn't re-download
# the world every time application code changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package -DskipTests

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S ratelimiter && adduser -S ratelimiter -G ratelimiter
COPY --from=build /build/target/*.jar app.jar
RUN chown ratelimiter:ratelimiter app.jar
USER ratelimiter

EXPOSE 8080

# Redis host/port/instance id are all environment-driven (never hardcoded) so the
# same image can be run as app-instance-1/2/3 with different SERVER_PORT values
# against one shared Redis, per Phase 3 of the spec.
#
# JAVA_OPTS defaults to a modest, container-friendly heap cap rather than letting the
# JVM's container-aware ergonomics size itself off however much memory the *host* Docker
# VM happens to have. Without this, 3 instances each defaulting to ~25% of a shared,
# possibly small Docker Desktop VM (e.g. the ~3.7GB WSL2 default on an 8GB Windows laptop)
# can collectively exceed what's actually available under concurrent load, triggering an
# OOM kill of one instance mid-test - which looks like a networking failure (nginx
# reporting "connection refused" for the dead upstream) but is really a memory budgeting
# gap. Override per-environment via `JAVA_OPTS=-Xmx768m docker compose up` if needed.
ENV JAVA_OPTS="-Xms256m -Xmx768m -Xss256k -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:+AlwaysPreTouch -Djava.net.preferIPv4Stack=true"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

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
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

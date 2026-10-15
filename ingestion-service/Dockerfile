# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper first so the layer is cached unless the wrapper changes.
COPY gradle/ gradle/
COPY gradlew gradlew
COPY gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.properties
RUN chmod +x gradlew

# Pre-fetch dependencies (cached unless build files change).
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon --quiet || true

# Copy source and build.
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Run as a non-root user.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseZGC", "-Dspring.threads.virtual.enabled=true", "-jar", "app.jar"]

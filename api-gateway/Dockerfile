# --- Stage 1: Build ---
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

COPY . .

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# --- Stage 2: Runtime ---
FROM eclipse-temurin:25-jre-alpine AS runner
WORKDIR /app

COPY --from=builder --chown=nobody:nobody /build/build/libs/*-SNAPSHOT.jar app.jar

USER nobody

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "app.jar"]
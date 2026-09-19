# --- Stage 1: Build ---
FROM eclipse-temurin:26-jdk-alpine AS builder
WORKDIR /build

COPY . .

# Виправляємо закінчення рядків Windows (CRLF -> LF) та робимо файл виконуваним
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# --- Stage 2: Runtime ---
FROM eclipse-temurin:26-jre-alpine AS runner
WORKDIR /app

COPY --from=builder --chown=nobody:nobody /build/build/libs/*-SNAPSHOT.jar app.jar

USER nobody

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "app.jar"]
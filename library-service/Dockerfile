# --- Stage 1: Build ---
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test
RUN java -Djarmode=tools -jar build/libs/*-SNAPSHOT.jar extract --layers --launcher --destination extracted

# --- Stage 2: Runtime ---
FROM eclipse-temurin:25-jre-alpine AS runner
WORKDIR /app

EXPOSE 8080

RUN addgroup --system --gid 10001 spring && \
    adduser --system --uid 10001 --ingroup spring spring

COPY --chown=spring:spring --from=builder /build/extracted/dependencies/ ./
COPY --chown=spring:spring --from=builder /build/extracted/spring-boot-loader/ ./
COPY --chown=spring:spring --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --chown=spring:spring --from=builder /build/extracted/application/ ./

USER 10001:10001

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseContainerSupport", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
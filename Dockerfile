FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 court && useradd --uid 10001 --gid court --no-create-home court \
    && mkdir -p /app /data/uploads && chown -R court:court /app /data/uploads \
    && apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build --chown=court:court /build/target/sports-court-booking-1.0.0.jar app.jar
COPY --chmod=755 scripts/start-container.sh /app/start-container.sh
USER 10001:10001
ENV SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:${PORT:-${SERVER_PORT:-8080}}/health >/dev/null || exit 1
ENTRYPOINT ["/app/start-container.sh"]

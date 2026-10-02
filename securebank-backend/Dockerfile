# syntax=docker/dockerfile:1
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21.0.12.1_1-jre-alpine
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="securebank-api"       org.opencontainers.image.description="SecureBank Core Banking API"       org.opencontainers.image.version="${VERSION}"       org.opencontainers.image.revision="${REVISION}"       org.opencontainers.image.source="https://github.com/Alexandregv1080p/SecureBank"
# usuário sem shell de login e sem home gravável: nada a explorar caso o processo seja comprometido
RUN addgroup -S app && adduser -S -H -s /sbin/nologin app -G app
WORKDIR /app
COPY --from=build --chown=app:app /src/target/securebank-backend-*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5   CMD wget -q --spider http://localhost:8080/api/v1/actuator/health/readiness || exit 1
# ExitOnOutOfMemoryError: morrer rápido e ser reiniciado é melhor que ficar meio vivo
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

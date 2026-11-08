# syntax=docker/dockerfile:1.7
# Imagen del backend (Spring Boot 3.5, Java 21).
# El contexto de build es la RAÍZ del repo: el build necesita api/openapi.yaml (fuera de backend/)
# para generar las interfaces con openapi-generator. Uso:
#   docker build -f backend/Dockerfile -t asuntos-horas-backend .
# Los tests NO corren aquí: los corre el job `backend` del CI (mvnw verify).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/backend

# 1) Solo wrapper + pom: la capa de dependencias se reutiliza mientras el pom no cambie.
#    Se guarda en una capa (no en un cache mount) porque la caché `type=gha` del CI exporta
#    capas, no cache mounts.
#    `unzip` es necesario: sin él, mvnw descarga el .tar.gz en lugar del .zip y la verificación
#    de `distributionSha256Sum` (calculada sobre el .zip, que también usa mvnw.cmd) falla.
RUN apt-get update \
  && apt-get install -y --no-install-recommends unzip \
  && rm -rf /var/lib/apt/lists/*
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# 2) Contrato y fuentes.
COPY api /workspace/api
COPY backend/src src
RUN ./mvnw -B -ntp -DskipTests package \
  && find target -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
     -exec cp {} /workspace/app.jar \; \
  && test -s /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
# busybox wget (incluido en alpine) se usa para el healthcheck de compose.
RUN addgroup -S app && adduser -S -G app -h /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
  CMD wget -q -O /dev/null http://localhost:8081/api/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

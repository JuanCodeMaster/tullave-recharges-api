# ---------- Etapa 1: construcción ----------
# Compila y empaqueta con Maven dentro de la imagen; no se necesita Java ni Maven en el host.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Primero solo el descriptor y el wrapper: esta capa se cachea mientras no cambien las dependencias.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

# Luego el código fuente: cambios en src no invalidan la capa de dependencias.
COPY src/ src/
RUN ./mvnw -q -B package -DskipTests

# Extrae el jar en capas (dependencias, loader, snapshots, aplicación) para optimizar la imagen final.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted

# ---------- Etapa 2: ejecución ----------
# Solo JRE: imagen más pequeña y menor superficie de ataque.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# curl para el healthcheck y usuario sin privilegios para ejecutar la aplicación.
RUN apk add --no-cache curl \
    && addgroup -S app && adduser -S app -G app

# Capas en orden de menor a mayor frecuencia de cambio.
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar recharges-api-1.0.0.jar"]

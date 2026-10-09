# ============================================================
# Imagen de GGaming en dos etapas:
#   1) "build": compila con Maven (pesada, se descarta).
#   2) final: solo Java + el .jar (liviana y con menos superficie de ataque).
# Funciona en Intel/AMD y en ARM (como el servidor gratis de Oracle).
# ============================================================

# ---------- Etapa 1: compilar ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
# Primero solo el pom: Docker guarda en caché las dependencias y no las baja en cada cambio de código.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
# Los tests corren en GitHub Actions; acá solo empaquetamos.
RUN mvn -B -q package -DskipTests

# ---------- Etapa 2: ejecutar ----------
FROM eclipse-temurin:25-jre
# Usuario sin privilegios: si alguien encontrara un fallo en la app, no sería "root".
RUN useradd --system --uid 10001 --no-create-home ggaming \
    && mkdir -p /data/uploads && chown -R ggaming /data
WORKDIR /app
COPY --from=build /build/target/ggaming-*.jar app.jar
USER ggaming

# Base de datos y fotos en /data (es un "volumen": sobrevive a reinicios y actualizaciones).
ENV DB_PATH=/data/ggaming.db \
    UPLOADS_DIR=/data/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

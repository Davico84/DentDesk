# Imagen del backend para el despliegue (Render, plan gratuito: 512 MB, 0,1 CPU). Ver docs/deployment.md
# y add-cloud-deployment design D2. Adaptado del de HSO (mismo backend de partida).
# Las pruebas no se repiten aquí: se despliega `main`, que solo recibe merges con `mvn verify` en verde.

# --- 1. Build: JDK 25 + Maven wrapper ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
# Dependencias primero: capa cacheable mientras no cambie el pom.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
# Solo el jar ejecutable de Spring Boot, extraído como recomienda Spring Boot para el AOT cache.
RUN ./mvnw -q -B -DskipTests package \
    && java -Djarmode=tools -jar target/backend-*.jar extract --destination /build/extracted \
    && mv /build/extracted/backend-*.jar /build/extracted/app.jar

# --- 2. Runtime: JRE 25, solo la app extraída + AOT cache, sin privilegios ---
FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /build/extracted /app
WORKDIR /app

# Opciones de la JVM, iguales en el entrenamiento y en la ejecución (el AOT cache lo exige):
# - G1 explícito: con < 2 CPU la JVM elegiría SerialGC y no podría cargar el heap archivado.
# - Solo C1 (TieredStopAtLevel=1): con 0,1 CPU el compilador C2 retrasa el arranque.
# - Sin código nativo en el cache (adapters/stubs): depende del CPU donde se construye la imagen, y
#   Render construye y ejecuta en máquinas distintas (en HSO: SIGILL al arrancar).
ENV JVM_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=70 -Xss512k -XX:TieredStopAtLevel=1 -XX:+UnlockDiagnosticVMOptions -XX:-AOTAdapterCaching -XX:-AOTStubCaching"

# AOT cache de Java 25 (JEP 483/514/515): una corrida de entrenamiento que arranca el contexto y sale
# tras el refresh, SIN base de datos (Flyway apagado, Hibernate sin leer metadatos de la BD).
# A diferencia de HSO, application.yml resuelve la base y el JWT con variables propias (DB_URL,
# DB_USERNAME, DB_PASSWORD, JWT_SECRET de >= 32 bytes): se pasan ESAS, con valores de entrenamiento
# que no son secretos y no viajan a la ejecución (solo existen en este RUN).
RUN DB_URL=jdbc:postgresql://localhost:5432/training \
    DB_USERNAME=training \
    DB_PASSWORD=training \
    JWT_SECRET=solo-entrenamiento-aot-no-es-un-secreto-0123456789abcdef \
    java $JVM_OPTS -Xmx358m -XX:AOTCacheOutput=/app/app.aot \
      -Dspring.context.exit=onRefresh \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -jar app.jar \
    && chown -R app:app /app

USER app
ENV JAVA_TOOL_OPTIONS="$JVM_OPTS -XX:AOTCache=/app/app.aot -XX:+ExitOnOutOfMemoryError"
# Render inyecta PORT; en local, 8080 (server.port: ${PORT:8080}). La API vive bajo /api.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

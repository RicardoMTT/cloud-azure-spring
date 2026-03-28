# Estrategia de multi-stage build
# Una etapa compila el proyecto y la otra solo corre el JAR. Así la imagen final es pequeña y no incluye Maven ni el código fuente.

# ── Etapa 1: compilar ──────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copia primero solo el pom.xml para aprovechar el cache de capas
# Si no cambia el pom.xml, Docker no re-descarga dependencias
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Ahora copia el código fuente y compila
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Etapa 2: imagen final ──────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copia el JAR generado en la etapa anterior
COPY --from=build /app/target/*.jar app.jar

# Puerto que expone tu Spring Boot (ajusta si usas otro)
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
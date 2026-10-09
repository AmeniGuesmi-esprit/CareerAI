# ---------- Étape 1 : build Maven ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache des dépendances (ne se relance que si le pom.xml change)
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- Étape 2 : image d'exécution ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --uid 1001 spring
COPY --from=build /app/target/*.jar app.jar
USER spring

EXPOSE 8222
ENTRYPOINT ["java", "-jar", "app.jar"]

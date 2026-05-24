# Stage 1: Bauen (Maven)
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Baut das .jar File ohne die Tests auszuführen
RUN mvn clean package -DskipTests

# Stage 2: Ausführen (Java)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Kopiert das fertige .jar File aus Stage 1
COPY --from=build /app/target/*.jar app.jar
# Port freigeben (Standard Spring Boot)
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
# ---- Stage 1: build the jar (needs the JDK and Maven) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy only pom.xml first and download dependencies. Docker caches this layer,
# so dependencies are re-downloaded only when pom.xml changes, not on every code change.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests are run by CI (they need Docker for Testcontainers), not during the image build.
RUN mvn -B -q package -DskipTests

# ---- Stage 2: the image that actually runs (JRE only, no Maven, no source code) ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Do not run the application as root.
RUN useradd --system --no-create-home ledger
USER ledger

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

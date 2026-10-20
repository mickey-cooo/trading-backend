# ---- dev: JDK + Maven wrapper, runs spring-boot:run so devtools reloads mounted sources ----
FROM eclipse-temurin:25-jdk AS dev
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
# separate layer: dependencies only re-download when pom.xml changes
RUN ./mvnw -B -q dependency:go-offline
COPY src src
EXPOSE 8080
CMD ["./mvnw", "spring-boot:run"]

# ---- build: package the jar (tests run in CI, not here) ----
FROM dev AS build
RUN ./mvnw -B -q package -DskipTests

# ---- prod: JRE only, non-root, no Maven, no sources ----
FROM eclipse-temurin:25-jre AS prod
RUN useradd --system --uid 1001 app
WORKDIR /app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app
EXPOSE 8080
# secrets (DB_*, JWT_SECRET_KEY, ADMIN_*) come from runtime env, never from the image
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

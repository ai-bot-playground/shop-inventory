# ---- Build stage: compile and package the Spring Boot executable jar ----
FROM gradle:9.6.0-jdk25 AS build
WORKDIR /home/gradle/project
# Copy build config first for better layer caching of dependencies
COPY --chown=gradle:gradle settings.gradle build.gradle ./
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true
# Copy sources and build only the bootJar (skips the extra -plain.jar)
COPY --chown=gradle:gradle src ./src
RUN gradle --no-daemon clean bootJar

# ---- Runtime stage: minimal JRE ----
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# syntax=docker/dockerfile:1

# --- Build: compile with the Maven wrapper; ~/.m2 is a BuildKit cache, so dependencies aren't re-downloaded ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src src
# Tests need Docker (Testcontainers) and run with ./mvnw test, not inside the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar "$(find target -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
        extract --layers --launcher --destination target/extracted

# --- Runtime: JRE only, non-root, one layer per Spring Boot layer so code changes don't re-ship dependencies ---
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app

COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER app
EXPOSE 8080 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

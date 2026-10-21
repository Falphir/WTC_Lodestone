# The hub: API, dashboard and WebSocket in one jar. Built by deploy/docker/docker-compose.yml.
# Tests are skipped here -- they need a database and CI already ran them on the commit.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# CI=true makes the Gradle node plugin use `npm ci`: exactly the dashboard's package-lock.json
RUN CI=true ./gradlew bootJar --no-daemon \
    && cp "$(ls build/libs/*.jar | grep -v -- -plain.jar)" /hub.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home lodestone
COPY --from=build /hub.jar /app/hub.jar
USER lodestone
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/hub.jar"]

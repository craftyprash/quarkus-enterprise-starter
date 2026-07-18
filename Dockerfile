# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
# Tests run in CI (mvn clean verify); the image build skips them.
RUN mvn -B clean package -DskipTests

# Stage 2: Runtime — Quarkus fast-jar layout, non-root
FROM eclipse-temurin:21-jre
WORKDIR /deployments

RUN groupadd -r app && useradd -r -g app -u 185 app

COPY --from=build --chown=185 /build/target/quarkus-app/lib/ ./lib/
COPY --from=build --chown=185 /build/target/quarkus-app/*.jar ./
COPY --from=build --chown=185 /build/target/quarkus-app/app/ ./app/
COPY --from=build --chown=185 /build/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080
USER 185

ENV JAVA_OPTS_APPEND="-Duser.timezone=UTC -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS_APPEND -jar /deployments/quarkus-run.jar"]

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/quarkus-app/ ./quarkus-app/

EXPOSE 8080
ENV JAVA_OPTS="--enable-preview -Duser.timezone=UTC -Dnet.bytebuddy.experimental=true"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/quarkus-app/quarkus-run.jar"]

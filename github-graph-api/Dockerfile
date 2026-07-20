FROM maven:3.9.9-eclipse-temurin-21 AS source

WORKDIR /build

COPY pom.xml pom.xml
COPY src src

FROM source AS test

RUN mvn test

FROM source AS build

RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/github-graph-api-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]

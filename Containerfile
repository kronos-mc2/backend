FROM docker.io/library/eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -DskipTests package

FROM docker.io/library/eclipse-temurin:25-jre

WORKDIR /app

RUN useradd --system --uid 10001 --create-home appuser

COPY --from=build /workspace/target/backend-0.0.1-SNAPSHOT.jar /app/backend.jar

ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=test

EXPOSE 8080

USER appuser

ENTRYPOINT ["java", "-jar", "/app/backend.jar"]

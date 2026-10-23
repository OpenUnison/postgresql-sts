# Optional alternative to Maven/Jib's daemonless image build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -Djib.skip=true package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/task-manager-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]

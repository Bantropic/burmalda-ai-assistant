FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/agent-core-1.0-SNAPSHOT.jar app.jar
ENV MOCK_HOST=0.0.0.0
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]

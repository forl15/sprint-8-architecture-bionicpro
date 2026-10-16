FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/reports-1.0.0.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

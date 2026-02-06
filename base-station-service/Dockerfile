FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml ./common/
COPY base-station-service/pom.xml ./base-station-service/
COPY monitoring-service/pom.xml ./monitoring-service/
COPY notification-service/pom.xml ./notification-service/
COPY api-gateway/pom.xml ./api-gateway/
COPY auth-service/pom.xml ./auth-service/
COPY tmf-api/pom.xml ./tmf-api/
RUN mvn dependency:go-offline -B
COPY common/src ./common/src
COPY base-station-service ./base-station-service
RUN mvn clean package -DskipTests -pl base-station-service -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/base-station-service/target/base-station-service-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]


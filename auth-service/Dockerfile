FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml ./common/
COPY base-station-service/pom.xml ./base-station-service/
COPY monitoring-service/pom.xml ./monitoring-service/
COPY notification-service/pom.xml ./notification-service/
COPY api-gateway/pom.xml ./api-gateway/
COPY auth-service/pom.xml ./auth-service/
RUN mvn dependency:go-offline -B
COPY common/src ./common/src
COPY auth-service/src ./auth-service/src
RUN mvn clean package -DskipTests -pl auth-service -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/auth-service/target/auth-service-1.0.0.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]


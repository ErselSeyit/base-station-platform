FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY eureka-server/pom.xml ./eureka-server/
COPY base-station-service/pom.xml ./base-station-service/
COPY monitoring-service/pom.xml ./monitoring-service/
COPY notification-service/pom.xml ./notification-service/
COPY api-gateway/pom.xml ./api-gateway/
RUN mvn dependency:go-offline -B
COPY base-station-service ./base-station-service
RUN mvn clean package spring-boot:repackage -DskipTests -pl base-station-service -am

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/base-station-service/target/base-station-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]


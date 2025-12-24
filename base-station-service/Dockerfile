FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY base-station-service/pom.xml ./base-station-service/
RUN mvn dependency:go-offline -B
COPY base-station-service ./base-station-service
RUN mvn clean package -DskipTests -pl base-station-service -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/base-station-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]


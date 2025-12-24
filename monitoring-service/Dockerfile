FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY monitoring-service/pom.xml ./monitoring-service/
RUN mvn dependency:go-offline -B
COPY monitoring-service ./monitoring-service
RUN mvn clean package -DskipTests -pl monitoring-service -am

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/monitoring-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]


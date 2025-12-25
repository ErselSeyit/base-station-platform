FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY auth-service/pom.xml ./auth-service/
RUN mvn dependency:go-offline -B
COPY auth-service/src ./auth-service/src
RUN mvn clean package -DskipTests -pl auth-service

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/auth-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]


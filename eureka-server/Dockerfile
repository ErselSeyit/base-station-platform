FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY eureka-server/pom.xml ./eureka-server/
RUN mvn dependency:go-offline -B
COPY eureka-server ./eureka-server
RUN mvn clean package -DskipTests -pl eureka-server -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/eureka-server/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]


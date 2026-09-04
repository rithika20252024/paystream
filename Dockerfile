# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/paystream-1.0.0.jar paystream-1.0.0.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "paystream-1.0.0.jar"]

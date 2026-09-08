FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY mcp-gateway-server/pom.xml mcp-gateway-server/pom.xml
COPY mock-user-service/pom.xml mock-user-service/pom.xml

COPY mcp-gateway-server/src mcp-gateway-server/src
RUN mvn -B -ntp -pl mcp-gateway-server -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/mcp-gateway-server/target/mcp-gateway-server-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

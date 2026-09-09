FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY mcp-gateway-server/pom.xml mcp-gateway-server/pom.xml
COPY mock-user-service/pom.xml mock-user-service/pom.xml
COPY mock-order-service/pom.xml mock-order-service/pom.xml
COPY mock-product-service/pom.xml mock-product-service/pom.xml
COPY mock-inventory-service/pom.xml mock-inventory-service/pom.xml
COPY mock-payment-service/pom.xml mock-payment-service/pom.xml
COPY mock-logistics-service/pom.xml mock-logistics-service/pom.xml

COPY mock-user-service/src mock-user-service/src
RUN mvn -B -ntp -pl mock-user-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/mock-user-service/target/mock-user-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

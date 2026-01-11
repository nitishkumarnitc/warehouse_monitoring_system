FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
# Copy fat JARs
COPY --from=build /build/warehouse-service/target/warehouse-service-fat.jar /app/
COPY --from=build /build/central-monitoring-service/target/central-monitoring-service-fat.jar /app/
# Default entrypoint (overridden by docker-compose)
ENTRYPOINT ["java", "-jar", "warehouse-service-fat.jar"]

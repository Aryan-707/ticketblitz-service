FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app
# We assume standard maven layout
COPY backend/pom.xml .
COPY backend/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx350m", "-Xms200m", "-XX:+UseSerialGC", "-Dspring.jpa.open-in-view=false", "-jar", "app.jar"]

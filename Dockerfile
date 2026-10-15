FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY core/pom.xml core/pom.xml
COPY server/pom.xml server/pom.xml
COPY core/src core/src
COPY server/src server/src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system maze && useradd --system --gid maze maze
COPY --from=build /app/server/target/maze-server-1.0.0-SNAPSHOT.jar app.jar
USER maze
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# Build stage
FROM docker.io/library/maven:3-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# Runtime stage
FROM docker.io/library/eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S spring \
    && adduser -S spring -G spring \
    && mkdir -p /app/storage \
    && chown -R spring:spring /app/storage

COPY --from=build /workspace/target/*.jar /app/app.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]

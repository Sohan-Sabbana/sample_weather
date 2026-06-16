# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -q clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /workspace/target/validation-service.jar /app/validation-service.jar

USER app

ENV SPRING_PROFILES_ACTIVE=k8s \
    SERVER_PORT=8081 \
    ENV=k8s

EXPOSE 8081

HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://localhost:8081/api/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/validation-service.jar"]

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

COPY --from=build /workspace/target/weather-api.jar /app/weather-api.jar

USER app

ENV SPRING_PROFILES_ACTIVE=k8s \
    SERVER_PORT=8080 \
    ENV=k8s

EXPOSE 8080

# In k8s the kubelet does the health-checking via the probe in the Deployment.
# This HEALTHCHECK is mostly useful for `docker run` outside k8s.
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/weather-api.jar"]

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/weather-api.jar /app/weather-api.jar

ENV LOG_DIR=/var/log/weather-api \
    SERVER_PORT=8080 \
    ENV=docker

RUN mkdir -p ${LOG_DIR}

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "java -DLOG_DIR=$LOG_DIR -Dserver.port=$SERVER_PORT -jar /app/weather-api.jar"]

# weather-api — sample app for the Jenkins CD log-analyzer

A small Spring Boot Weather API used as the deployable artifact for the CD
log-analyzer pipeline. It exists so you have:

- A real service to **build**, **deploy**, **MAT**-test and **regression**-test in Jenkins.
- Endpoints to hit from TestNG, so each pipeline stage emits meaningful logs.
- Structured JSON logs carrying a **trace id** end-to-end so the analyzer can
  pull the whole story from Elasticsearch with a single query.

---

## 1. What's in the box

```
weather-api/
├── pom.xml
├── Jenkinsfile                      <-- Build / Deploy / MAT / Regression
├── Dockerfile                       <-- optional container deploy
├── testng-mat.xml                   <-- MAT suite
├── testng-regression.xml            <-- Regression suite
├── src/main/java/com/example/weather/
│   ├── WeatherApiApplication.java
│   ├── config/
│   │   ├── OpenApiConfig.java       <-- Swagger metadata
│   │   └── TraceIdFilter.java       <-- reads/issues X-Trace-Id -> MDC
│   ├── controller/                  <-- 14 REST endpoints
│   ├── service/                     <-- in-memory store + seeded data
│   ├── model/                       <-- City, WeatherReading, Forecast, Alert
│   └── exception/                   <-- 404/400 handlers
├── src/main/resources/
│   ├── application.yml
│   └── logback-spring.xml           <-- JSON logs -> Elasticsearch-ready
└── src/test/java/com/example/weather/tests/
    ├── BaseApiTest.java             <-- generates per-test trace ids
    ├── mat/MatSmokeTests.java
    └── regression/{City,Weather,Alert}RegressionTests.java
```

## 2. Endpoints (14)

Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

| # | Method | Path                              | Purpose                          |
|---|--------|-----------------------------------|----------------------------------|
| 1 | GET    | `/api/cities`                     | list all cities                  |
| 2 | GET    | `/api/cities/{id}`                | get city by id                   |
| 3 | POST   | `/api/cities`                     | create city                      |
| 4 | PUT    | `/api/cities/{id}`                | update city                      |
| 5 | DELETE | `/api/cities/{id}`                | delete city                      |
| 6 | GET    | `/api/weather/current/{city}`     | current weather                  |
| 7 | GET    | `/api/weather/forecast/{city}`    | n-day forecast (`?days=`)        |
| 8 | GET    | `/api/weather/history/{city}`     | n-day history (`?days=`)         |
| 9 | GET    | `/api/alerts`                     | list alerts                      |
|10 | GET    | `/api/alerts/{id}`                | get alert                        |
|11 | POST   | `/api/alerts`                     | raise alert                      |
|12 | DELETE | `/api/alerts/{id}`                | delete alert                     |
|13 | GET    | `/api/health`                     | health probe (used by deploy)    |
|14 | GET    | `/api/version`                    | deployed version                 |

## 3. Run locally

```bash
mvn clean package -DskipTests
java -jar target/weather-api.jar
# then open http://localhost:8080/swagger-ui.html
```

Run the suites against it:

```bash
# Minimum Acceptance Tests
mvn -Pmat test -Dapi.base.url=http://localhost:8080 -Dtest.stage=mat

# Full regression
mvn -Pregression test -Dapi.base.url=http://localhost:8080 -Dtest.stage=regression
```

Both produce JSON logs under `target/logs/`.

## 4. The trace-id flow (the heart of the log analyzer)

```
  ┌─────────────────┐       X-Trace-Id: abc123        ┌──────────────────┐
  │  TestNG test    │ ──────────────────────────────▶ │ Spring Boot app  │
  │  (MAT/Reg)      │                                 │ TraceIdFilter    │
  │  BaseApiTest    │                                 │  puts traceId    │
  │  generates UUID │                                 │  in Logback MDC  │
  └────────┬────────┘                                 └────────┬─────────┘
           │                                                   │
           ▼                                                   ▼
  tests-<stage>.json                                  weather-api.json
  (traceId, testName, suite, build)                   (traceId, level, logger, build)
           │                                                   │
           └───────────────────┬───────────────────────────────┘
                               ▼
                    Filebeat / Logstash
                               │
                               ▼
                       Elasticsearch index
                  weather-api-logs-YYYY.MM.DD
                               │
                               ▼
        Query in Kibana / your CD log-analyzer service:
        traceId:"abc123"   →   every log line for that one API call
        build:"42" AND pipelineStage:"regression"
                          →   every regression-stage line for build #42
```

Every log line — server-side and test-side — is JSON and contains:

```json
{
  "@timestamp": "2026-05-21T09:11:23.812Z",
  "level": "INFO",
  "logger": "com.example.weather.service.WeatherService",
  "message": "Current weather city=Bengaluru temp=27.4C conditions='Sunny'",
  "traceId": "abc123-...",
  "spanId": "9f12...",
  "pipelineStage": "regression",
  "service": "weather-api",
  "version": "1.0.0",
  "env": "ci",
  "build": "42"
}
```

That means your CD log analyzer only needs to know the trace id (or
`build` + `pipelineStage`) to reconstruct the full causal chain of a
failure in Elasticsearch.

## 5. The Jenkins pipeline

`Jenkinsfile` ships with four stages and a teardown:

1. **Build** — `mvn clean package -DskipTests`, archives the jar.
2. **Deploy** — starts the jar with `LOG_DIR`, `BUILD_NUMBER`, `ENV` set; waits
   for `/api/health` to return `UP`.
3. **MAT** — `mvn -Pmat test` against the deployed instance. One quick happy
   path per critical endpoint. Fails the build fast.
4. **Regression** — `mvn -Pregression test`, full sweep across cities,
   weather, alerts and validation paths.
5. **post.always** — stops the app and archives `logs/**` so the analyzer
   (or Filebeat) can pick everything up.

Every stage echoes `[stage=... run=...]` on stdout for the Jenkins build log
itself, while the JVMs write structured JSON to `logs/`.

## 6. Wiring logs into Elasticsearch

You have two clean options. Pick whichever fits your infra:

### Option A — Filebeat on the Jenkins agent

Point Filebeat at `${LOG_DIR}/*.json`:

```yaml
filebeat.inputs:
  - type: log
    paths: [ "/var/jenkins_home/workspace/*/logs/*.json" ]
    json.keys_under_root: true
    json.add_error_key: true
output.elasticsearch:
  hosts: ["http://elasticsearch:9200"]
  index: "weather-api-logs-%{+yyyy.MM.dd}"
```

Because the encoder already writes one JSON object per line with all the
right fields, no Logstash parsing is required.

### Option B — Direct from Logback

Add a `LogstashTcpSocketAppender` in `logback-spring.xml` pointing at
Logstash. Keep the file appender too so Jenkins still archives a copy.

## 7. Useful Kibana / Elasticsearch queries for the analyzer

```
# every log line from one failing test
traceId:"abc123-..."

# everything regression printed on build 42
build:"42" AND pipelineStage:"regression"

# only server-side errors in this build
service:"weather-api" AND level:"ERROR" AND build:"42"

# correlate a failed MAT test with its server-side stacktrace
testName:"healthIsUp" AND build:"42"
```

## 8. Extending it

- Add new endpoints under `controller/` — the filter, logging and Swagger
  pick them up automatically.
- Add a new TestNG class under `tests/regression/` and reference it from
  `testng-regression.xml`. The base class already gives you a trace id.
- Tag stages by passing a different `-Dtest.stage=...` value in the
  Jenkinsfile (e.g. `perf`, `security`) and your analyzer can filter on it.

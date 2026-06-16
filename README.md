# weather-api — sample app + Jenkins CD pipeline with Elasticsearch logging

A small Spring Boot Weather API used as the deployable artifact for a CD
log-analyzer pipeline. End-to-end you get:

1. Code in this repo, built and tested by Jenkins.
2. Container image pushed to Docker Hub.
3. App deployed as pods to Docker Desktop Kubernetes.
4. Pod stdout (JSON) shipped to Elasticsearch by a Filebeat DaemonSet.
5. MAT + Regression test suites run from Jenkins against the deployed pods.
6. Test JVM logs bulk-uploaded to Elasticsearch too.
7. Single `traceId` per test joins server-side and test-side logs in Kibana.

```
                              ┌────────────────────────┐
                              │  Docker Desktop K8s     │
                              │  ┌──────────────────┐   │
                              │  │ weather-api pods │ ──┼─── stdout (JSON)
                              │  └──────────────────┘   │           │
                              │  ┌──────────────────┐   │           ▼
                              │  │ filebeat daemon  │ ──┼──> Elasticsearch ──> Kibana
                              │  └──────────────────┘   │     (in-cluster)    :30601
                              │  ┌──────────────────┐   │      :30200
                              │  │ elasticsearch +  │   │           ▲
                              │  │ kibana           │   │           │
                              │  └──────────────────┘   │       _bulk
                              └────────────────────────┘           │
                                                                   │
                                                  ┌────────────────┘
                                                  │
                          ┌───────────────────────┴───────────┐
                          │  Jenkins (container on this host) │
                          │  build -> docker push ->          │
                          │  kubectl apply -> wait ->         │
                          │  MAT -> Regression ->             │
                          │  ship test logs to ES             │
                          └───────────────────────────────────┘
```

---

## 0. Prerequisites

- Docker Desktop with Kubernetes **enabled** (Settings -> Kubernetes -> Enable).
- A Docker Hub account.
- Git, Maven, JDK 17 (only needed if you want to run the app locally outside k8s).

Verify your cluster:
```powershell
kubectl config use-context docker-desktop
kubectl get nodes
```

---

## 1. One-time setup: deploy the logging stack

```powershell
cd C:\Users\sohansa\cd-log-analyzer-sample-api
powershell -ExecutionPolicy Bypass -File .\scripts\setup-logging.ps1
```

This installs into your cluster:
- `logging/elasticsearch` (single-node, NodePort 30200)
- `logging/kibana`         (NodePort 30601)
- `logging/filebeat`       (DaemonSet, ships pod logs to ES)

Wait until both pods are `Ready`, then open Kibana:
**http://localhost:30601** → Stack Management → Index Patterns → create
- `weather-logs-*`       (pod logs - the source of truth)
- `weather-logs-cd-*`    (optional Jenkins console logs)

---

## 2. One-time setup: Jenkins container

This builds and runs a Jenkins image that already has `docker`, `kubectl`,
`maven`, `git`, `jq`, `curl` — and a kubeconfig pointing at your Docker
Desktop cluster.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\run-jenkins.ps1
```

The script prints the **initial admin password**. Open
**http://localhost:8080**, install the suggested plugins (the Jenkinsfile
needs `git`, `pipeline`, `junit`, `ansicolor`, `timestamper`, `credentials-binding`).

Verify everything from inside the container:
```powershell
docker exec jenkins kubectl get nodes
docker exec jenkins docker version
```

---

## 3. One-time setup: Jenkins credentials

In Jenkins: **Manage Jenkins → Credentials → System → Global → Add Credentials**

| ID         | Kind                          | Notes                                              |
|------------|-------------------------------|----------------------------------------------------|
| `dockerhub`| Username with password        | Docker Hub username + Personal Access Token        |
| `github`   | Username with password        | GitHub username + PAT (for SCM checkout if private)|

Use a Docker Hub PAT (Account Settings → Security → Access Tokens),
not your password.

---

## 4. One-time setup: create the pipeline

Dashboard → New Item → name it `weather-api` → **Pipeline** → OK.

- Pipeline → Definition: **Pipeline script from SCM**
- SCM: Git
- Repo URL: `https://github.com/Sohan-Sabbana/sample_weather.git`
- Credentials: `github` (skip if the repo is public)
- Branch: `*/main`
- Script Path: `Jenkinsfile`
- Save.

When you click **Build with Parameters**, set `DOCKERHUB_USER` to your
Docker Hub username (default is the placeholder in the file).

---

## 5. Run the pipeline

The Jenkinsfile runs these stages:

| Stage              | What happens                                                       |
|--------------------|--------------------------------------------------------------------|
| Checkout           | clones the repo                                                    |
| Build              | `mvn clean package -DskipTests`                                    |
| Docker build & push| `docker build` + `docker push <DOCKERHUB_USER>/weather-api:<BUILD>` |
| Deploy to k8s      | `kubectl apply` namespaces, deployment (with image substituted), service, then `rollout restart` + `rollout status` |
| Wait for endpoint  | polls `http://host.docker.internal:30080/api/health`               |
| MAT                | TestNG smoke suite vs the deployed pods                            |
| Regression         | full TestNG regression suite                                       |
| Post (always)      | dumps pod logs, archives `logs/**`, ships test logs to ES          |

After a successful run, in Kibana → Discover:

```
# everything for this build (server pods + test JVM)
build:"42"

# every log line from one specific test
traceId:"abc123-..."

# only server-side errors in this build
service:"weather-api" AND level:"ERROR" AND build:"42"

# correlate a failing test with the server stacktrace it triggered
testName:"healthIsUp" AND build:"42"
```

---

## 6. Project layout

```
weather-api/
├── pom.xml
├── Jenkinsfile                          <-- build/push/deploy/test/ship
├── Dockerfile                           <-- multi-stage, non-root, stdout JSON
├── testng-mat.xml                       <-- MAT suite
├── testng-regression.xml                <-- Regression suite
├── src/main/java/com/example/weather/
│   ├── WeatherApiApplication.java
│   ├── config/
│   │   ├── OpenApiConfig.java
│   │   ├── DownstreamClientConfig.java  <-- RestTemplate forwards trace+test headers
│   │   └── TraceIdFilter.java           <-- X-Trace-Id/X-Test-Name -> MDC -> log JSON
│   ├── controller/                      <-- REST endpoints
│   ├── service/                         <-- incl. ValidationClient (calls MS-2/MS-3)
│   ├── model/
│   └── exception/
├── src/main/resources/
│   ├── application.yml
│   └── logback-spring.xml               <-- 'k8s' profile = stdout JSON
├── src/test/java/com/example/weather/tests/
│   ├── BaseApiTest.java                 <-- per-test traceId, talks to /api/...
│   ├── mat/MatSmokeTests.java
│   └── regression/{City,Weather,Alert}RegressionTests.java
├── src/test/resources/logback-test.xml
├── validation-service/                  <-- downstream MS-2/MS-3 (Spring Boot)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/...                     <-- /api/validate/{city,alert}, same log JSON
├── k8s/
│   ├── 00-namespaces.yaml
│   ├── app/
│   │   ├── deployment.yaml              <-- IMAGE_PLACEHOLDER, probes, labels
│   │   ├── service.yaml                 <-- NodePort 30080
│   │   ├── validation-deployment.yaml   <-- VALIDATION_IMAGE_PLACEHOLDER
│   │   └── validation-service.yaml      <-- ClusterIP 8081 (internal)
│   └── logging/
│       ├── elasticsearch.yaml           <-- single-node, NodePort 30200
│       ├── kibana.yaml                  <-- NodePort 30601
│       └── filebeat.yaml                <-- DaemonSet + RBAC + autodiscover
└── scripts/
    ├── setup-logging.ps1                <-- installs ES + Kibana + Filebeat
    └── jenkins/
        ├── Dockerfile.jenkins           <-- Jenkins + docker + kubectl + mvn
        └── run-jenkins.ps1              <-- builds & runs the Jenkins container
```

---

## 7. Run the app outside Kubernetes (local dev)

```powershell
mvn clean package -DskipTests
java -jar target/weather-api.jar
# Swagger UI: http://localhost:8080/swagger-ui.html
```

Without the `k8s` profile, the app uses pretty stdout + `logs/weather-api.json`.

Run the tests against it:
```powershell
mvn -Pmat        test "-Dapi.base.url=http://localhost:8080" "-Dtest.stage=mat"
mvn -Pregression test "-Dapi.base.url=http://localhost:8080" "-Dtest.stage=regression"
```

---

## 8. Troubleshooting

### "No static resource ." 500 on http://localhost:8080
The weather-api is running and you hit `/`. It has no controller for `/`. Use:
`/swagger-ui.html`, `/api/health`, `/api/cities`, etc.

### Jenkins container can't reach the cluster
From inside the container: `kubectl get nodes`. If it fails with x509 errors,
re-run `scripts/jenkins/run-jenkins.ps1` — it rewrites the kubeconfig server
URL to `https://host.docker.internal:6443`.

### Filebeat shipping nothing
`kubectl -n logging logs ds/filebeat | head -50`. Common causes:
- Elasticsearch not ready (check `kubectl -n logging get pods`).
- Your pods don't have `co.elastic.logs/enabled: "true"` annotation — the
  weather-api Deployment in this repo does set it.

### App pod CrashLoopBackOff
`kubectl -n weather logs deploy/weather-api`. Usually image pull or memory
limit too low.

### Index pattern is empty in Kibana
Wait 30s after first deploy. Then check directly:
```powershell
curl http://localhost:30200/_cat/indices?v
```
You should see `weather-logs-YYYY.MM.DD`.

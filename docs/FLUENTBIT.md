# Fluent Bit log shipping

Three log streams go to Elasticsearch:

| Stream | Source | Fluent Bit deployment | ES index pattern |
|--------|--------|----------------------|------------------|
| **Pod logs** | `weather-api` stdout (JSON) | K8s DaemonSet | `weather-logs-*` |
| **CD logs** | Jenkins build `log` files | Docker container | `weather-logs-cd-*` |
| **Test logs** | `workspace/*/logs/tests-*.json` | Docker container | `weather-logs-test-*` |

All three streams ship to the **same** Elasticsearch (Docker Desktop ES on
`http://localhost:9200`). This is required so the CD log analyzer can join pod
logs to test logs on `build` + `traceId`. Pod logs carry the real build number
(the deploy step sets a `build` pod label) and `testName` (propagated from the
`X-Test-Name` header into the server MDC).

## 1. Pod logs (Kubernetes)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-logging.ps1
```

This deploys Elasticsearch + Kibana + **Fluent Bit DaemonSet** (`k8s/logging/fluent-bit.yaml`).

- Tails `/var/log/containers/*.log` on each node
- Keeps only namespace `weather`
- Parses JSON from logback (`traceId`, `build`, `testName`, `level`, …)
- Writes to the **Docker Desktop ES → http://localhost:9200** (default `ES_HOST`
  in `k8s/logging/fluent-bit.yaml` is `host.docker.internal`), the same ES the
  Jenkins pipeline, the analyzer, and Kibana use.

Verify:

```powershell
kubectl -n logging logs ds/fluent-bit --tail=40
kubectl -n weather get pods
curl http://localhost:9200/_cat/indices?v
```

Kibana: **http://localhost:5601** → index pattern `weather-logs-*`

> To use the in-cluster ES instead, set `ES_HOST=elasticsearch.logging.svc.cluster.local`
> on the DaemonSet (and point Jenkins `ES_URL` + the analyzer at that cluster).
> The default keeps everything in one place so correlation works out of the box.

## 2. Jenkins CD + test logs (Docker)

Jenkins runs in Docker; Fluent Bit runs as another container with **`jenkins_home` mounted read-only**.

```powershell
# Same path as scripts/jenkins/run-jenkins.ps1 uses
$env:JENKINS_HOME = "$HOME\jenkins_home"
powershell -ExecutionPolicy Bypass -File .\scripts\setup-fluentbit-jenkins.ps1
```

Ships to **http://localhost:9200** (Docker Compose ES). Kibana: **http://localhost:5601**

Index patterns:

- `weather-logs-cd-*` — pipeline console output
- `weather-logs-test-*` — MAT/regression JSON logs

Verify:

```powershell
docker logs fluent-bit-jenkins --tail 30
curl http://localhost:9200/_cat/indices?v
```

### One Elasticsearch for everything (default)

By default everything lands in the Docker Desktop ES on `http://localhost:9200`:

- Pod logs: the K8s DaemonSet ships to `host.docker.internal:9200`.
- CD + test logs: the Jenkins-side Fluent Bit / bulk script ship to `localhost:9200`.
- The analyzer queries `ES_URL=http://host.docker.internal:9200`.

No extra steps are needed for correlation. If you prefer the in-cluster ES
(`30200`) as the single store, flip `ES_HOST` on the DaemonSet to
`elasticsearch.logging.svc.cluster.local` and set Jenkins `ES_URL` + the
analyzer `--es-url` to `http://host.docker.internal:30200`.

## 3. Kibana queries

```
# All logs for build 6
build:"6"

# Pod + test for one trace
traceId:"7dea84ed-029a-40b0-bc15-986d3cac52b3"

# Jenkins CD only
log_type:"cd" AND jenkins_job:"weather"

# Test failures
log_type:"test" AND level:"ERROR"
```

## 4. Fallback: bulk upload script

`scripts/ship-test-logs-to-es.sh` in the Jenkinsfile still works if Fluent Bit is not running for tests.

## 5. Legacy Filebeat

`k8s/logging/filebeat.yaml` is kept for reference. `setup-logging.ps1` removes the Filebeat DaemonSet if present.

Config files live under `config/fluent-bit/`.

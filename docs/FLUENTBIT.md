# Fluent Bit log shipping

Elasticsearch is the store for **pod/container logs** (plus optional CD console
logs). Test JVM logs are intentionally NOT shipped to ES.

| Stream | Source | Fluent Bit deployment | ES index pattern |
|--------|--------|----------------------|------------------|
| **Pod logs** | `weather-api` (and downstream services) stdout (JSON) | K8s DaemonSet | `weather-logs-*` |
| **CD logs** (optional) | Jenkins build `log` files | Docker container | `weather-logs-cd-*` |
| **Test logs** | `workspace/*/logs/tests-*.json` | NOT shipped - local Jenkins artifacts only | n/a |

Everything that is shipped goes to the **same** Elasticsearch (Docker Desktop ES
on `http://localhost:9200`). Pod logs carry the real build number (the deploy
step sets a `build` pod label) and `testName` (propagated from the `X-Test-Name`
header into the server MDC). The CD log analyzer joins a failed test to its pod
logs by **`testName` + `build`** - it reads the test-side detail from the local
flow artifacts, not from ES, so test logs do not need to be in ES.

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

- `weather-logs-cd-*` — pipeline console output (optional)

Verify:

```powershell
docker logs fluent-bit-jenkins --tail 30
curl http://localhost:9200/_cat/indices?v
```

### One Elasticsearch for everything (default)

By default everything lands in the Docker Desktop ES on `http://localhost:9200`:

- Pod logs: the K8s DaemonSet ships to `host.docker.internal:9200`.
- CD console logs (optional): the Jenkins-side Fluent Bit ships to `localhost:9200`.
- Test JVM logs: not shipped - kept as local Jenkins artifacts for the flow report and analyzer workspace collector.
- The analyzer queries `ES_URL=http://host.docker.internal:9200`.

No extra steps are needed for correlation. If you prefer the in-cluster ES
(`30200`) as the single store, flip `ES_HOST` on the DaemonSet to
`elasticsearch.logging.svc.cluster.local` and set Jenkins `ES_URL` + the
analyzer `--es-url` to `http://host.docker.internal:30200`.

## 3. Kibana queries

```
# All logs for build 6
build:"6"

# Pod logs for one trace (cross-service request chain)
traceId:"7dea84ed-029a-40b0-bc15-986d3cac52b3"

# Pod logs for one failed test in a build
testName:"findPinForValidAddress" AND build:"6"

# Jenkins CD only
log_type:"cd" AND jenkins_job:"weather"

# Pod errors for a build
build:"6" AND level:"ERROR"
```

## 4. Legacy Filebeat

`k8s/logging/filebeat.yaml` is kept for reference. `setup-logging.ps1` removes the Filebeat DaemonSet if present.

Config files live under `config/fluent-bit/`.

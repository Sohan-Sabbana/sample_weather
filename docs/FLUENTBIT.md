# Fluent Bit log shipping

Three log streams go to Elasticsearch:

| Stream | Source | Fluent Bit deployment | ES index pattern |
|--------|--------|----------------------|------------------|
| **Pod logs** | `weather-api` stdout (JSON) | K8s DaemonSet | `weather-logs-*` |
| **CD logs** | Jenkins build `log` files | Docker container | `weather-logs-cd-*` |
| **Test logs** | `workspace/*/logs/tests-*.json` | Docker container | `weather-logs-test-*` |

## 1. Pod logs (Kubernetes)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-logging.ps1
```

This deploys Elasticsearch + Kibana + **Fluent Bit DaemonSet** (`k8s/logging/fluent-bit.yaml`).

- Tails `/var/log/containers/*.log` on each node
- Keeps only namespace `weather`
- Parses JSON from logback (`traceId`, `build`, `level`, …)
- Writes to in-cluster ES → **http://localhost:30200**

Verify:

```powershell
kubectl -n logging logs ds/fluent-bit --tail=40
kubectl -n weather get pods
curl http://localhost:30200/_cat/indices?v
```

Kibana (K8s): **http://localhost:30601** → index pattern `weather-logs-*`

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

### One Elasticsearch for everything

If you only want K8s ES (`30200`), point Fluent Bit Jenkins at it:

```powershell
$env:ES_HOST = "host.docker.internal"
$env:ES_PORT = "30200"
# edit docker-compose.fluentbit.yml environment for fluent-bit service, then:
docker compose -f docker-compose.fluentbit.yml up -d fluent-bit
```

Update Jenkins `ES_URL` in the Jenkinsfile to the same host/port.

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

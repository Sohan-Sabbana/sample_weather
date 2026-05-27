# Deploy Fluent Bit DaemonSet only, shipping pod logs to Docker Desktop Elasticsearch
# (the elasticsearch container on localhost:9200 — not the in-cluster logging stack).
#
#   powershell -ExecutionPolicy Bypass -File .\scripts\setup-fluentbit-k8s-docker-es.ps1

$ErrorActionPreference = "Stop"

Write-Host "Kube context: $(kubectl config current-context)"

Write-Host "`n--- Namespace logging ---"
kubectl apply -f k8s/00-namespaces.yaml

Write-Host "`n--- Fluent Bit DaemonSet ---"
kubectl apply -f k8s/logging/fluent-bit.yaml

Write-Host "`n--- Point Fluent Bit at host Elasticsearch (Docker) ---"
# apply resets env to in-cluster defaults; set env after apply, then restart.
kubectl -n logging set env daemonset/fluent-bit `
    ES_HOST=host.docker.internal `
    ES_PORT=9200 `
    --overwrite
kubectl -n logging rollout restart daemonset/fluent-bit

Write-Host "`n--- Wait for rollout ---"
kubectl -n logging rollout status daemonset/fluent-bit --timeout=180s

Write-Host "`n--- Pods ---"
kubectl -n logging get pods -l app=fluent-bit

Write-Host @"

Done. Pod logs from namespace 'weather' -> weather-logs-* on http://localhost:9200
Kibana (Docker): http://localhost:5601 — create data views for weather-logs-* and weather-logs-test-*

"@

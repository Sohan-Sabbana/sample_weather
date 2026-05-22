# One-time installer for the logging stack in Docker Desktop Kubernetes.
# Run from the project root:
#     powershell -ExecutionPolicy Bypass -File .\scripts\setup-logging.ps1

$ErrorActionPreference = "Stop"

Write-Host "Using kube context: " -NoNewline
kubectl config current-context

Write-Host "`n--- Creating namespaces ---"
kubectl apply -f k8s/00-namespaces.yaml

Write-Host "`n--- Deploying Elasticsearch ---"
kubectl apply -f k8s/logging/elasticsearch.yaml

Write-Host "`n--- Deploying Kibana ---"
kubectl apply -f k8s/logging/kibana.yaml

Write-Host "`n--- Deploying Filebeat (DaemonSet) ---"
kubectl apply -f k8s/logging/filebeat.yaml

Write-Host "`n--- Waiting for Elasticsearch to be ready (this can take 1-3 mins) ---"
kubectl -n logging rollout status statefulset/elasticsearch --timeout=300s

Write-Host "`n--- Waiting for Kibana to be ready (also 1-3 mins) ---"
kubectl -n logging rollout status deployment/kibana --timeout=300s

Write-Host "`n--- Status ---"
kubectl -n logging get pods,svc

Write-Host "`nDone."
Write-Host "  Elasticsearch:  http://localhost:30200"
Write-Host "  Kibana:         http://localhost:30601"
Write-Host ""
Write-Host "In Kibana, go to Stack Management -> Index Patterns and create:"
Write-Host "  weather-logs-*       (for pod logs shipped by Filebeat)"
Write-Host "  weather-logs-test-*  (for test JVM logs shipped by Jenkins)"

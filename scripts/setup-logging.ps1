# Installs Elasticsearch, Kibana, and Fluent Bit into Docker Desktop Kubernetes.
# Fluent Bit replaces Filebeat for shipping weather-api pod logs to ES.
#
#   powershell -ExecutionPolicy Bypass -File .\scripts\setup-logging.ps1
#
# Optional: also start Docker-side Fluent Bit for Jenkins (CD + test logs):
#   powershell -ExecutionPolicy Bypass -File .\scripts\setup-fluentbit-jenkins.ps1

$ErrorActionPreference = "Stop"

Write-Host "Using kube context: " -NoNewline
kubectl config current-context

Write-Host "`n--- Creating namespaces ---"
kubectl apply -f k8s/00-namespaces.yaml

Write-Host "`n--- Deploying Elasticsearch ---"
kubectl apply -f k8s/logging/elasticsearch.yaml

Write-Host "`n--- Deploying Kibana ---"
kubectl apply -f k8s/logging/kibana.yaml

Write-Host "`n--- Deploying Fluent Bit (DaemonSet) ---"
kubectl apply -f k8s/logging/fluent-bit.yaml

# Remove legacy Filebeat if it was installed earlier (avoid duplicate shippers)
$fb = kubectl -n logging get ds filebeat -o name 2>$null
if ($fb) {
    Write-Host "`n--- Removing legacy Filebeat DaemonSet ---"
    kubectl -n logging delete ds filebeat --ignore-not-found
    kubectl delete clusterrole,clusterrolebinding filebeat --ignore-not-found 2>$null
}

Write-Host "`n--- Waiting for Elasticsearch ---"
kubectl -n logging rollout status statefulset/elasticsearch --timeout=300s

Write-Host "`n--- Waiting for Kibana ---"
kubectl -n logging rollout status deployment/kibana --timeout=300s

Write-Host "`n--- Waiting for Fluent Bit ---"
kubectl -n logging rollout status daemonset/fluent-bit --timeout=180s

Write-Host "`n--- Status ---"
kubectl -n logging get pods,svc

Write-Host "`nDone."
Write-Host "  Elasticsearch (K8s):  http://localhost:30200"
Write-Host "  Kibana (K8s):         http://localhost:30601"
Write-Host ""
Write-Host "Kibana index patterns:"
Write-Host "  weather-logs-*        pod stdout (Fluent Bit DaemonSet)"
Write-Host "  weather-logs-test-*   test JVM + flow logs (Fluent Bit on Jenkins OR bulk script)"
Write-Host "  weather-logs-cd-*     Jenkins pipeline console (Fluent Bit on Jenkins)"
Write-Host ""
Write-Host "For Jenkins CD/test shipping, run:"
Write-Host "  powershell -ExecutionPolicy Bypass -File .\scripts\setup-fluentbit-jenkins.ps1"

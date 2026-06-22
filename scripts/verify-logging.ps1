# Quick health check for the logging pipeline (Docker ES + K8s Fluent Bit + indices).
#   powershell -ExecutionPolicy Bypass -File .\scripts\verify-logging.ps1

$ErrorActionPreference = "Continue"

Write-Host "=== Docker containers ==="
docker ps --format "table {{.Names}}\t{{.Status}}" | Select-String -Pattern "elastic|kibana|fluent|jenkins"

Write-Host "`n=== K8s Fluent Bit (pod logs) ==="
kubectl -n logging get pods -l app=fluent-bit 2>$null
$fbEnv = kubectl -n logging get ds fluent-bit -o jsonpath="{.spec.template.spec.containers[0].env[*].name}={.spec.template.spec.containers[0].env[*].value}" 2>$null
Write-Host "ES target: $fbEnv"

Write-Host "`n=== Weather pods ==="
kubectl -n weather get pods 2>$null

Write-Host "`n=== Elasticsearch indices (weather*) ==="
$today = (Get-Date).ToUniversalTime().ToString("yyyy.MM.dd")
$podIdx = "weather-logs-$today"
$testIdx = "weather-logs-test-$today"
docker exec elasticsearch curl -s "http://localhost:9200/_cat/indices/weather*?v&s=index:desc" 2>$null | Select-Object -First 8
$podCount = docker exec elasticsearch curl -s "http://localhost:9200/${podIdx}/_count" 2>$null | ConvertFrom-Json
Write-Host "`nToday (UTC): $podIdx -> $($podCount.count) docs"
$testRaw = docker exec elasticsearch curl -s "http://localhost:9200/${testIdx}/_count" 2>$null
if ($testRaw -match '"count"') {
    $t = $testRaw | ConvertFrom-Json
    Write-Host "             $testIdx -> $($t.count) docs"
} else {
    Write-Host "             $testIdx -> no index yet (run Jenkins build to ship test logs)"
}

Write-Host "`n=== Kibana ==="
Write-Host "  http://localhost:5601/app/discover"
Write-Host "  Pod logs  -> data view 'Weather pod logs'   (weather-logs-*)"
Write-Host "  Test logs -> data view 'Weather test logs' (weather-logs-test-*)"
Write-Host "  Filter example: build:21"

Write-Host "`nNote: Pod logs use the K8s Fluent Bit DaemonSet in namespace logging."
Write-Host "      Docker container fluent-bit-jenkins is optional (CD console logs only)."

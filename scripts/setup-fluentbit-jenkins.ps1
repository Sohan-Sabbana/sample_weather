# Starts Elasticsearch + Kibana + Fluent Bit to ship Jenkins CD and test logs to ES.
# Requires Jenkins home on the host (same path as run-jenkins.ps1).
#
#   powershell -ExecutionPolicy Bypass -File .\scripts\setup-fluentbit-jenkins.ps1

$ErrorActionPreference = "Stop"

$JENKINS_HOME = if ($env:JENKINS_HOME) { $env:JENKINS_HOME } else { "$HOME\jenkins_home" }

if (-not (Test-Path $JENKINS_HOME)) {
    Write-Error "Jenkins home not found at $JENKINS_HOME. Start Jenkins first or set `$env:JENKINS_HOME."
    exit 1
}

$env:JENKINS_HOME = $JENKINS_HOME
Write-Host "Using JENKINS_HOME=$JENKINS_HOME"

Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    docker compose -f docker-compose.fluentbit.yml up -d
    Write-Host ""
    Write-Host "Fluent Bit is tailing:"
    Write-Host "  $JENKINS_HOME\jobs\*\builds\*\log          -> weather-logs-cd-*"
    Write-Host "  (test JVM logs are kept as local Jenkins artifacts, not shipped to ES)"
    Write-Host ""
    Write-Host "Elasticsearch: http://localhost:9200"
    Write-Host "Kibana:        http://localhost:5601"
    Write-Host ""
    Write-Host "Verify:"
    Write-Host "  docker logs fluent-bit-jenkins --tail 30"
    Write-Host "  curl http://localhost:9200/_cat/indices?v"
} finally {
    Pop-Location
}

# Creates Kibana 8 data views for weather log indices (idempotent).
#
#   powershell -ExecutionPolicy Bypass -File .\scripts\kibana-create-data-views.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\kibana-create-data-views.ps1 -KibanaUrl http://localhost:30601

param(
    [string] $KibanaUrl = "http://localhost:5601"
)

$ErrorActionPreference = "Stop"

$views = @(
    @{ name = "Weather pod logs";   pattern = "weather-logs-*";      time = "@timestamp" },
    @{ name = "Weather test logs";  pattern = "weather-logs-test-*"; time = "@timestamp" },
    @{ name = "Weather CD logs";    pattern = "weather-logs-cd-*";   time = "@timestamp" }
)

foreach ($v in $views) {
    $body = @{
        data_view = @{
            title           = $v.pattern
            name            = $v.name
            timeFieldName   = $v.time
        }
    } | ConvertTo-Json -Depth 5

    try {
        Invoke-RestMethod -Uri "$KibanaUrl/api/data_views/data_view" `
            -Method POST `
            -ContentType "application/json" `
            -Headers @{ "kbn-xsrf" = "true" } `
            -Body $body | Out-Null
        Write-Host "Created data view: $($v.name) ($($v.pattern))"
    }
    catch {
        $msg = $_.ErrorDetails.Message
        if ($msg -match "already exists|Conflict") {
            Write-Host "Data view already exists: $($v.name)"
        }
        else {
            throw
        }
    }
}

Write-Host "`nOpen Discover: $KibanaUrl/app/discover"

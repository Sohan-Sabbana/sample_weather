# Pushes a newline-delimited JSON log file into Elasticsearch using _bulk.
#
# Usage:
#     .\scripts\ship-test-logs-to-es.ps1 -LogFile .\logs\tests-mat.json -Suite mat
#
# Env vars:
#     ES_URL        default http://localhost:9200
#     BUILD_NUMBER  default 'local'

param(
    [Parameter(Mandatory = $true)] [string] $LogFile,
    [Parameter(Mandatory = $true)] [string] $Suite
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $LogFile) -or ((Get-Item $LogFile).Length -eq 0)) {
    Write-Host "No log file or empty: $LogFile"
    return
}

$EsUrl = if ($env:ES_URL) { $env:ES_URL } else { "http://localhost:9200" }
$Build = if ($env:BUILD_NUMBER) { $env:BUILD_NUMBER } else { "local" }
$Index = "weather-logs-test-" + (Get-Date -Format "yyyy.MM.dd")

function Add-JsonFieldIfMissing {
    param([string]$Json, [string]$Field, [string]$Value)
    if ($Json -match ('"' + [regex]::Escape($Field) + '"\s*:')) {
        return $Json
    }
    return ($Json -replace '\}\s*$', (',"' + $Field + '":"' + $Value + '"}'))
}

function Add-LogFromMessage {
    param([string]$Json)
    if ($Json -match '"log"\s*:') { return $Json }
    if ($Json -notmatch '"message"\s*:') { return $Json }
    $o = $Json | ConvertFrom-Json
    if ($null -ne $o.message -and -not $o.PSObject.Properties['log']) {
        $o | Add-Member -NotePropertyName log -NotePropertyValue ([string]$o.message) -Force
    }
    return ($o | ConvertTo-Json -Compress -Depth 20)
}

$sb = [System.Text.StringBuilder]::new()
$count = 0
Get-Content -LiteralPath $LogFile | ForEach-Object {
    $line = $_.TrimEnd()
    if (-not $line) { return }
    $doc = $line
    $doc = Add-JsonFieldIfMissing -Json $doc -Field "source" -Value "tests"
    $doc = Add-JsonFieldIfMissing -Json $doc -Field "suite"  -Value $Suite
    $doc = Add-JsonFieldIfMissing -Json $doc -Field "build"  -Value $Build
    $doc = Add-LogFromMessage -Json $doc
    [void]$sb.AppendLine('{"index":{}}')
    [void]$sb.AppendLine($doc)
    $count++
}

Write-Host "Shipping $count log events from $LogFile -> $EsUrl/$Index"

$resp = Invoke-RestMethod -Uri "$EsUrl/$Index/_bulk?refresh=true" `
    -Method POST `
    -ContentType "application/x-ndjson" `
    -Body ($sb.ToString())

if ($resp.errors) {
    $failed = @($resp.items | Where-Object { $_.index.error })
    $sample = ($failed | Select-Object -First 1 | ConvertTo-Json -Depth 8).Substring(0, [Math]::Min(1500, ($failed | Select-Object -First 1 | ConvertTo-Json -Depth 8).Length))
    throw "Bulk reported errors ($($failed.Count) failed). First: $sample"
}

$indexed = @($resp.items | Where-Object { $_.index.result -in @("created", "updated") }).Count
Write-Host "Bulk upload OK ($indexed indexed)."

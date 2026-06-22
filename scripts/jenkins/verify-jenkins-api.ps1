# Verify Jenkins API token from jenkins.local.env (read-only smoke test).
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\verify-jenkins-api.ps1

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\load-jenkins-env.ps1"

if (-not $env:JENKINS_TOKEN) {
    Write-Error "JENKINS_TOKEN is not set in jenkins.local.env"
    exit 1
}

$pair = "${env:JENKINS_USER}:${env:JENKINS_TOKEN}"
$b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$base = $env:JENKINS_URL.TrimEnd("/")

Write-Host "Checking $base/me/api/json as $($env:JENKINS_USER) ..."
try {
    $me = Invoke-RestMethod -Uri "$base/me/api/json" -Headers @{ Authorization = "Basic $b64" }
    Write-Host "OK - authenticated as: $($me.fullName) (id=$($me.id))"
} catch {
    Write-Error "Authentication failed (401). Check JENKINS_USER and JENKINS_TOKEN in jenkins.local.env"
    exit 1
}

Write-Host "Checking job/weather/lastBuild ..."
try {
    $build = Invoke-RestMethod -Uri "$base/job/weather/lastBuild/api/json?tree=number,building,result" -Headers @{ Authorization = "Basic $b64" }
    Write-Host "OK - weather lastBuild #$($build.number) result=$($build.result) building=$($build.building)"
} catch {
    Write-Error "Cannot read job/weather - ensure the job exists and the user has read permission"
    exit 1
}

Write-Host "Jenkins API verification passed."

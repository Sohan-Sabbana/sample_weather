# Trigger a parameterized weather Jenkins build (MASTER_CD=true).
# Loads credentials from gitignored jenkins.local.env.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\trigger-weather-build.ps1

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\load-jenkins-env.ps1"

$pair = "${env:JENKINS_USER}:${env:JENKINS_TOKEN}"
$b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$base = $env:JENKINS_URL.TrimEnd("/")
$headers = @{ Authorization = "Basic $b64" }

$crumb = (Invoke-RestMethod -Uri "$base/crumbIssuer/api/json" -Headers $headers).crumb
$headers["Jenkins-Crumb"] = $crumb

$body = @{
    MASTER_CD      = "true"
    DOCKERHUB_USER = "sparebuddy"
}

Write-Host "Triggering weather build at $base ..."
$response = Invoke-WebRequest -Uri "$base/job/weather/buildWithParameters?delay=0sec" `
    -Method POST -Headers $headers -Body $body -UseBasicParsing

if ($response.StatusCode -eq 201 -or $response.StatusCode -eq 200) {
    $last = Invoke-RestMethod -Uri "$base/job/weather/lastBuild/api/json?tree=number,url" -Headers $headers
    Write-Host "Build triggered: #$($last.number) $($last.url)"
} else {
    Write-Error "Unexpected response: $($response.StatusCode)"
}

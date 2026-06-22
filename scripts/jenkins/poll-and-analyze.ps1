# Poll Jenkins from outside the agent, sync console + artifacts, run cd-analyze.
#
# Usage (loads credentials from gitignored jenkins.local.env):
#   powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\poll-and-analyze.ps1 -Job weather -Build last -Print
#
# Or set env vars manually:
#   $env:JENKINS_URL = "http://localhost:8080"
#   $env:JENKINS_USER = "sohansa"
#   $env:JENKINS_TOKEN = "your-api-token"

param(
    [Parameter(Mandatory = $true)]
    [string]$Job,

    [string]$Build = "last",
    [string]$EsUrl = "http://localhost:9200",
    [string]$KubeNs = "weather",
    [string]$OutDir = ".\analyzer-out",
    [string]$JenkinsUrl,
    [string]$JenkinsUser,
    [string]$JenkinsToken,
    [switch]$Print,
    [switch]$SkipEnvFile
)

$ErrorActionPreference = "Stop"

$envFile = Join-Path $PSScriptRoot "jenkins.local.env"
if (-not $SkipEnvFile -and (Test-Path $envFile)) {
    . "$PSScriptRoot\load-jenkins-env.ps1"
}

if (-not $JenkinsUrl) { $JenkinsUrl = $env:JENKINS_URL }
if (-not $JenkinsUser) { $JenkinsUser = $env:JENKINS_USER }
if (-not $JenkinsToken) { $JenkinsToken = $env:JENKINS_TOKEN }

if (-not $JenkinsUrl -or -not $JenkinsUser -or -not $JenkinsToken) {
    Write-Error @"
Jenkins credentials missing. Either:
  1) Copy scripts/jenkins/jenkins.local.env.example -> jenkins.local.env and set JENKINS_TOKEN, or
  2) Set JENKINS_URL, JENKINS_USER, JENKINS_TOKEN environment variables.
"@
    exit 1
}

$cdAnalyze = Get-Command cd-analyze -ErrorAction SilentlyContinue
if (-not $cdAnalyze) {
    Write-Host "cd-analyze not in PATH - running via Jenkins container ..."
    $tokenMasked = if ($JenkinsToken.Length -gt 4) { "***" + $JenkinsToken.Substring($JenkinsToken.Length - 4) } else { "***" }
    Write-Host "Using user=$JenkinsUser url=$JenkinsUrl token=$tokenMasked"

    $dockerArgs = @(
        "exec", "-u", "jenkins",
        "-e", "JENKINS_URL=$JenkinsUrl",
        "-e", "JENKINS_USER=$JenkinsUser",
        "-e", "JENKINS_TOKEN=$JenkinsToken",
        "jenkins",
        "cd-analyze", "poll",
        "--jenkins-url", $JenkinsUrl,
        "--jenkins-user", $JenkinsUser,
        "--jenkins-token", $JenkinsToken,
        "--job", $Job,
        "--build", $Build,
        "--es-url", "http://host.docker.internal:9200",
        "--kube-ns", $KubeNs,
        "--kube-selector", "app=weather-api,app=validation-service",
        "--out-dir", "/tmp/analyzer-poll-$Job-$Build"
    )
    if ($Print) { $dockerArgs += "--print" }
    & docker @dockerArgs
    exit $LASTEXITCODE
}

$cmd = @(
    "cd-analyze", "poll",
    "--jenkins-url", $JenkinsUrl,
    "--jenkins-user", $JenkinsUser,
    "--jenkins-token", $JenkinsToken,
    "--job", $Job,
    "--build", $Build,
    "--es-url", $EsUrl,
    "--kube-ns", $KubeNs,
    "--kube-selector", "app=weather-api,app=validation-service",
    "--out-dir", $OutDir
)
if ($Print) { $cmd += "--print" }

Write-Host "Running poll for $Job build $Build as $JenkinsUser ..."
& $cmd[0] $cmd[1..($cmd.Length - 1)]
exit $LASTEXITCODE

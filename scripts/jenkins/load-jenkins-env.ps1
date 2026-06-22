# Load scripts/jenkins/jenkins.local.env into the current PowerShell session.
# Usage:
#   . .\scripts\jenkins\load-jenkins-env.ps1
#   cd-analyze poll --job weather --build last ...

$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot "jenkins.local.env"

if (-not (Test-Path $envFile)) {
    Write-Error "Missing $envFile - copy jenkins.local.env.example to jenkins.local.env and set JENKINS_TOKEN."
    exit 1
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $eq = $line.IndexOf("=")
    if ($eq -lt 1) { return }
    $name = $line.Substring(0, $eq).Trim()
    $value = $line.Substring($eq + 1).Trim()
    Set-Item -Path "Env:$name" -Value $value
}

Write-Host "Loaded Jenkins env from jenkins.local.env (user=$env:JENKINS_USER url=$env:JENKINS_URL)"

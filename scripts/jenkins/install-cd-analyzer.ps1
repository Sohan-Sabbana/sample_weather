# Installs cd-log-analyzer-engine (cd-analyze CLI) into the running Jenkins container.
# Required for the Jenkinsfile failure { cd-analyze ... } post step.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\install-cd-analyzer.ps1
#
# Optional env:
#   CD_ANALYZER_REF=main   Git branch/tag on github.com/Sohan-Sabbana/CD-analyzer

$ErrorActionPreference = "Stop"

$CONTAINER = "jenkins"
$REF = if ($env:CD_ANALYZER_REF) { $env:CD_ANALYZER_REF } else { "main" }

if (-not (docker ps --format "{{.Names}}" | Select-String -Pattern "^${CONTAINER}$" -Quiet)) {
    Write-Error "Container '$CONTAINER' is not running. Start Jenkins first."
    exit 1
}

Write-Host "Installing cd-analyze (ref=$REF) into $CONTAINER ..."
$pipSpec = "cd-log-analyzer-engine[llm] @ git+https://github.com/Sohan-Sabbana/CD-analyzer.git@${REF}"
docker exec -u root $CONTAINER bash -c "export DEBIAN_FRONTEND=noninteractive; apt-get update -qq; apt-get install -y -qq python3-venv python3-pip git >/dev/null; python3 -m venv /opt/cd-analyzer; /opt/cd-analyzer/bin/pip install --no-cache-dir -q -U pip; /opt/cd-analyzer/bin/pip install --no-cache-dir '$pipSpec'; ln -sf /opt/cd-analyzer/bin/cd-analyze /usr/local/bin/cd-analyze; cd-analyze --list"

Write-Host ""
Write-Host "Done. On the next FAILED build, Jenkins publishes 'CD Failure Analysis'."
Write-Host "Manual smoke test (uses build #21 workspace):"
Write-Host @"
  docker exec -u jenkins jenkins cd-analyze \
    --workspace /var/jenkins_home/workspace/weather \
    --job weather --build 21 \
    --console-log /var/jenkins_home/jobs/weather/builds/21/log \
    --es-url http://host.docker.internal:9200 \
    --kube-ns weather \
    --out-dir /tmp/analyzer-smoke --print
"@

# One-time patch for an existing my-jenkins container:
#   - install kubectl + docker CLI (if missing)
#   - copy kubeconfig
#   - fix K8s API URL for in-container access
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\patch-my-jenkins.ps1
#
# Container must be started with --group-add 0 so pipeline shells can use docker.sock.

$ErrorActionPreference = "Stop"

$CONTAINER = "jenkins"
$KUBE_DIR_HOST = "$env:USERPROFILE\.kube"

if (-not (docker ps --format "{{.Names}}" | Select-String -Pattern "^${CONTAINER}$" -Quiet)) {
    Write-Error "Container '$CONTAINER' is not running. Start Jenkins first."
    exit 1
}
if (-not (Test-Path "$KUBE_DIR_HOST\config")) {
    Write-Error "No kubeconfig at $KUBE_DIR_HOST\config. Enable Kubernetes in Docker Desktop."
    exit 1
}

Write-Host "Installing kubectl + docker CLI (if needed) ..."
docker exec -u root $CONTAINER bash -c "command -v kubectl >/dev/null || (curl -fsSL -o /usr/local/bin/kubectl https://dl.k8s.io/release/v1.31.0/bin/linux/amd64/kubectl && chmod +x /usr/local/bin/kubectl)"
docker exec -u root $CONTAINER bash -c "export DEBIAN_FRONTEND=noninteractive; command -v docker >/dev/null || (apt-get update -qq && apt-get install -y -qq docker.io)"
docker exec -u root $CONTAINER usermod -aG root jenkins 2>$null

Write-Host "Copying kubeconfig ..."
docker exec $CONTAINER mkdir -p /var/jenkins_home/.kube-host
docker cp "$KUBE_DIR_HOST\config" "${CONTAINER}:/var/jenkins_home/.kube-host/config"
docker exec $CONTAINER bash -c "mkdir -p /var/jenkins_home/.kube && cp /var/jenkins_home/.kube-host/config /var/jenkins_home/.kube/config && chown -R jenkins:jenkins /var/jenkins_home/.kube"

Write-Host "Fixing kube API URL for Docker Desktop (host port 1303) ..."
docker exec $CONTAINER bash -c "kubectl config set-cluster docker-desktop --server=https://host.docker.internal:1303 --tls-server-name=localhost --kubeconfig=/var/jenkins_home/.kube/config && chown jenkins:jenkins /var/jenkins_home/.kube/config"

Write-Host ""
Write-Host "Verification:"
docker exec -u jenkins -e KUBECONFIG=/var/jenkins_home/.kube/config $CONTAINER kubectl get nodes
docker exec -u jenkins $CONTAINER docker version --format "docker client {{.Client.Version}}"
docker exec jenkins bash -c "cat /proc/1/status | grep '^Groups:'"

Write-Host ""
Write-Host "Installing CD log analyzer (cd-analyze) ..."
powershell -ExecutionPolicy Bypass -File "$PSScriptRoot\install-cd-analyzer.ps1"

Write-Host ""
Write-Host "Done. Re-run the weather build."

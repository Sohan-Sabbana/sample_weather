# One-time patch for an existing my-jenkins container:
#   - install kubectl (if missing)
#   - copy kubeconfig (keeps kubernetes.docker.internal — required for TLS)
#   - verify docker + kubectl
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\patch-my-jenkins.ps1

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

Write-Host "Installing kubectl (if needed) ..."
docker exec -u root $CONTAINER bash -c "command -v kubectl >/dev/null || (curl -fsSL -o /usr/local/bin/kubectl https://dl.k8s.io/release/v1.31.0/bin/linux/amd64/kubectl && chmod +x /usr/local/bin/kubectl)"

Write-Host "Copying kubeconfig ..."
docker exec $CONTAINER mkdir -p /var/jenkins_home/.kube-host
docker cp "$KUBE_DIR_HOST\config" "${CONTAINER}:/var/jenkins_home/.kube-host/config"
docker exec $CONTAINER bash -c "mkdir -p /var/jenkins_home/.kube && cp /var/jenkins_home/.kube-host/config /var/jenkins_home/.kube/config && chown -R jenkins:jenkins /var/jenkins_home/.kube"

Write-Host ""
Write-Host "Verification:"
docker exec -u jenkins -e KUBECONFIG=/var/jenkins_home/.kube/config $CONTAINER kubectl get nodes
docker exec -u jenkins $CONTAINER docker version --format "docker client {{.Client.Version}}"

Write-Host ""
Write-Host "Done. Ensure Jenkins has credential id 'dockerhub', then re-run the build."

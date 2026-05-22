# Builds and runs a Jenkins container that has docker CLI, kubectl, maven
# and a kubeconfig wired up to your Docker Desktop Kubernetes.
#
# Usage:
#     powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\run-jenkins.ps1

$ErrorActionPreference = "Stop"

$IMAGE = "weather-jenkins:latest"
$HOME_DIR = "$HOME\jenkins_home"
$KUBE_DIR_HOST = "$HOME\.kube"

if (-not (Test-Path $HOME_DIR)) { New-Item -ItemType Directory -Path $HOME_DIR | Out-Null }
if (-not (Test-Path $KUBE_DIR_HOST)) {
    Write-Error "No kubeconfig found at $KUBE_DIR_HOST. Enable Kubernetes in Docker Desktop first."
    exit 1
}

Write-Host "Building $IMAGE ..."
docker build -t $IMAGE -f scripts/jenkins/Dockerfile.jenkins scripts/jenkins

Write-Host "Stopping any previous Jenkins container ..."
docker rm -f jenkins 2>$null | Out-Null

Write-Host "Starting Jenkins ..."
docker run -d --name jenkins `
    -p 8080:8080 -p 50000:50000 `
    -v "${HOME_DIR}:/var/jenkins_home" `
    -v "/var/run/docker.sock:/var/run/docker.sock" `
    -v "${KUBE_DIR_HOST}:/var/jenkins_home/.kube-host:ro" `
    -e KUBECONFIG=/var/jenkins_home/.kube/config `
    $IMAGE

# Copy the host kubeconfig and rewrite the server URL so it points to the
# host's Kubernetes API as seen FROM INSIDE the container.
docker exec jenkins bash -c '
    mkdir -p /var/jenkins_home/.kube &&
    cp /var/jenkins_home/.kube-host/config /var/jenkins_home/.kube/config &&
    sed -i "s|https://kubernetes.docker.internal:6443|https://host.docker.internal:6443|g" /var/jenkins_home/.kube/config &&
    sed -i "s|https://127.0.0.1:6443|https://host.docker.internal:6443|g" /var/jenkins_home/.kube/config
'

Write-Host ""
Write-Host "Jenkins is starting at http://localhost:8080"
Write-Host "Initial admin password:"
docker exec jenkins bash -lc "until [ -f /var/jenkins_home/secrets/initialAdminPassword ]; do sleep 2; done; cat /var/jenkins_home/secrets/initialAdminPassword"

Write-Host ""
Write-Host "Verify kubectl works from inside the container:"
Write-Host "  docker exec jenkins kubectl get nodes"

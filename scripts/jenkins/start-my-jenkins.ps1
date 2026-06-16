# Start the existing my-jenkins container with persisted job data (Docker volume jenkins_home).
# Does NOT rebuild the image — preserves your weather job, credentials, and Maven tool config.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\jenkins\start-my-jenkins.ps1

$ErrorActionPreference = "Stop"

$CONTAINER = "jenkins"
$IMAGE = "my-jenkins:2.541.1"
$VOLUME = "jenkins_home"
$KUBE_DIR_HOST = "$env:USERPROFILE\.kube"

if (-not (docker image inspect $IMAGE 2>$null)) {
    Write-Error "Image $IMAGE not found. Build or pull it first."
    exit 1
}

Write-Host "Starting $CONTAINER ($IMAGE) with volume $VOLUME ..."
docker rm -f $CONTAINER 2>$null | Out-Null

docker run -d --name $CONTAINER `
    -p 8080:8080 -p 50000:50000 `
    --group-add 0 `
    -v "${VOLUME}:/var/jenkins_home" `
    -v "/var/run/docker.sock:/var/run/docker.sock" `
    -e KUBECONFIG=/var/jenkins_home/.kube/config `
    $IMAGE

Write-Host "Patching kubectl, kubeconfig, docker CLI (if needed) ..."
powershell -ExecutionPolicy Bypass -File "$PSScriptRoot\patch-my-jenkins.ps1"

# Docker Desktop K8s API is on host port 1303; 127.0.0.1 is wrong inside the container.
docker exec $CONTAINER bash -c @'
if grep -q "127.0.0.1:1303" /var/jenkins_home/.kube/config 2>/dev/null; then
  kubectl config set-cluster docker-desktop \
    --server=https://host.docker.internal:1303 \
    --tls-server-name=localhost \
    --kubeconfig=/var/jenkins_home/.kube/config
  chown jenkins:jenkins /var/jenkins_home/.kube/config
fi
'@

Write-Host ""
Write-Host "Jenkins:  http://localhost:8080"
Write-Host "Jobs:     weather, my-job (from volume $VOLUME)"
Write-Host ""
Write-Host "Verify:"
Write-Host "  docker exec -u jenkins jenkins kubectl get nodes"
Write-Host "  docker exec -u jenkins jenkins docker version"

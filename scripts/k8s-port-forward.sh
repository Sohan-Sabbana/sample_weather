#!/usr/bin/env bash
# Expose weather-api inside the Jenkins container when Docker Desktop K8s does not
# publish NodePort 30080 on the host (desktop-control-plane / kind-based clusters).
#
# Usage:
#   bash scripts/k8s-port-forward.sh start   # background port-forward + health wait
#   bash scripts/k8s-port-forward.sh stop    # kill background port-forward

set -euo pipefail

NS="${KUBE_NS:-weather}"
SVC="${K8S_SVC:-weather-api}"
LOCAL_PORT="${APP_PORT:-30080}"
REMOTE_PORT="${K8S_SVC_PORT:-8080}"
BASE_URL="http://127.0.0.1:${LOCAL_PORT}"
PID_FILE="/tmp/pf-${SVC}.pid"
LOG_FILE="/tmp/pf-${SVC}.log"

stop_pf() {
  if [ -f "$PID_FILE" ]; then
    kill "$(cat "$PID_FILE")" 2>/dev/null || true
    rm -f "$PID_FILE"
  fi
  pkill -f "kubectl.*port-forward.*${SVC}" 2>/dev/null || true
}

start_pf() {
  stop_pf
  echo "Starting kubectl port-forward ${SVC} ${LOCAL_PORT}:${REMOTE_PORT} (ns=${NS}) ..."
  kubectl -n "$NS" port-forward "svc/${SVC}" "${LOCAL_PORT}:${REMOTE_PORT}" \
    --address 127.0.0.1 >"$LOG_FILE" 2>&1 &
  echo $! >"$PID_FILE"
  sleep 2

  echo "Waiting for ${BASE_URL}/api/health ..."
  for i in $(seq 1 60); do
    if curl -fsS "${BASE_URL}/api/health" >/dev/null; then
      echo "App reachable via port-forward after ${i} attempts"
      return 0
    fi
    sleep 2
  done

  echo "App not reachable via port-forward. kubectl -n ${NS} get pods:"
  kubectl -n "$NS" get pods || true
  echo "--- port-forward log ---"
  tail -20 "$LOG_FILE" 2>/dev/null || true
  return 1
}

case "${1:-start}" in
  start) start_pf ;;
  stop)  stop_pf ;;
  *) echo "usage: $0 {start|stop}"; exit 1 ;;
esac

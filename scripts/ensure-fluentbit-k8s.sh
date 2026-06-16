#!/usr/bin/env bash
# Ensures Fluent Bit DaemonSet ships weather namespace pod logs to Docker ES.
set -euo pipefail

ES_HOST="${ES_HOST:-host.docker.internal}"
ES_PORT="${ES_PORT:-9200}"

kubectl apply -f k8s/00-namespaces.yaml
kubectl apply -f k8s/logging/fluent-bit.yaml
kubectl -n logging set env daemonset/fluent-bit \
  ES_HOST="$ES_HOST" \
  ES_PORT="$ES_PORT" \
  --overwrite
kubectl -n logging rollout status daemonset/fluent-bit --timeout=120s
echo "Fluent Bit ready -> http://${ES_HOST}:${ES_PORT} (indices weather-logs-*)"

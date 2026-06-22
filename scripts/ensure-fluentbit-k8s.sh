#!/usr/bin/env bash
# Ensures Fluent Bit DaemonSet ships weather namespace pod logs to Docker ES.
set -euo pipefail

ES_HOST="${ES_HOST:-host.docker.internal}"
ES_PORT="${ES_PORT:-9200}"
ES_URL="http://${ES_HOST}:${ES_PORT}"
TODAY_INDEX="weather-logs-$(date -u +%Y.%m.%d)"

kubectl apply -f k8s/00-namespaces.yaml
kubectl apply -f k8s/logging/fluent-bit.yaml

CURRENT_HOST="$(kubectl -n logging get ds fluent-bit -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="ES_HOST")].value}' 2>/dev/null || true)"
if [ "$CURRENT_HOST" != "$ES_HOST" ]; then
  echo "Updating Fluent Bit ES_HOST: ${CURRENT_HOST:-<unset>} -> $ES_HOST"
  kubectl -n logging set env daemonset/fluent-bit ES_HOST="$ES_HOST" ES_PORT="$ES_PORT" --overwrite
  kubectl -n logging rollout restart daemonset/fluent-bit
fi

kubectl -n logging rollout status daemonset/fluent-bit --timeout=120s

# Quick sanity: ES reachable from the host (Fluent Bit uses hostNetwork + same target).
if curl -fsS --max-time 5 "${ES_URL}/_cluster/health" >/dev/null; then
  COUNT="$(curl -fsS --max-time 5 "${ES_URL}/${TODAY_INDEX}/_count" 2>/dev/null | python3 -c "import json,sys; print(json.load(sys.stdin).get('count',0))" 2>/dev/null || echo "?")"
  echo "Fluent Bit ready -> ${ES_URL} (${TODAY_INDEX}: ${COUNT} docs so far)"
else
  echo "WARNING: Elasticsearch not reachable at ${ES_URL} — start it: docker start elasticsearch" >&2
  exit 1
fi

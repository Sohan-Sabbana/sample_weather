#!/usr/bin/env bash
# Demo: run one failing regression test, ship logs, run cd-analyze.
# Usage (inside Jenkins container):
#   bash scripts/jenkins/run-demo-failure-analyzer.sh [BUILD_NUMBER]

set -uo pipefail

BUILD_NUMBER="${1:-24}"
MVN="${MVN:-/var/jenkins_home/tools/hudson.tasks.Maven_MavenInstallation/Maven-3.9/bin/mvn}"
WS="${WS:-/var/jenkins_home/workspace/weather}"
export KUBECONFIG="${KUBECONFIG:-/var/jenkins_home/.kube/config}"
export APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:30080}"
export LOG_DIR="${WS}/logs"
export ENV="${ENV:-ci}"

cd "$WS"
mkdir -p "$LOG_DIR"

bash scripts/k8s-port-forward.sh start
sleep 2

echo "=== Running demo failure test (build=${BUILD_NUMBER}) ==="
set +e
"$MVN" -B -ntp -Pregression test \
  -Dapi.base.url="$APP_BASE_URL" \
  -Dtest.stage=regression \
  -Dtest.run.id="weather-${BUILD_NUMBER}" \
  -DLOG_DIR="$LOG_DIR" \
  -DBUILD_NUMBER="$BUILD_NUMBER" \
  -DENV="$ENV" \
  -Dtest=AlertRegressionTests#demoAnalyzerFailure_ms3ValidationWarn
MVN_EXIT=$?
set -e
echo "Maven exit: $MVN_EXIT"

bash scripts/ship-test-logs-to-es.sh "$LOG_DIR/tests-regression.json" regression || true
sleep 8

echo "=== Running cd-analyze ==="
rm -rf "$LOG_DIR/analyzer"
cd-analyze \
  --workspace "$WS" \
  --job weather \
  --build "$BUILD_NUMBER" \
  --console-log "/var/jenkins_home/jobs/weather/builds/23/log" \
  --es-url http://host.docker.internal:9200 \
  --es-index "weather-logs-*" \
  --kube-ns weather \
  --out-dir "$LOG_DIR/analyzer" \
  --print || true

bash scripts/k8s-port-forward.sh stop || true

echo "=== Done. Evidence: ${LOG_DIR}/analyzer/evidence.json ==="

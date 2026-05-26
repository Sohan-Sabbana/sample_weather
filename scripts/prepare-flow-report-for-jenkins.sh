#!/usr/bin/env bash
# Builds logs/flow-report/ for Jenkins publishHTML (HTML + flows/*.log).
set -euo pipefail

LOG_DIR="${LOG_DIR:-logs}"

bash "$(dirname "$0")/merge-flow-reports.sh"

echo "Flow report bundle ready at $LOG_DIR/flow-report/"

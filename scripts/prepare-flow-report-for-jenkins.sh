#!/usr/bin/env bash
# Bundles flow-execution-report.html + flows/*.log for Jenkins publishHTML.
# Relative links (flows/...) work because both live under logs/flow-report/.
set -euo pipefail

LOG_DIR="${LOG_DIR:-logs}"
DEST="$LOG_DIR/flow-report"

rm -rf "$DEST"
mkdir -p "$DEST/flows"

bash "$(dirname "$0")/merge-flow-reports.sh"

if [ -f "$LOG_DIR/flow-execution-report.html" ]; then
  cp "$LOG_DIR/flow-execution-report.html" "$DEST/"
fi

if [ -d "$LOG_DIR/flows" ]; then
  cp -r "$LOG_DIR/flows/." "$DEST/flows/" 2>/dev/null || true
fi

echo "Flow report bundle ready at $DEST/"

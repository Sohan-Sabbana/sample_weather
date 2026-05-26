#!/usr/bin/env bash
# Merges per-stage flow reports. Tables already carry inline styles from Java;
# this script concatenates full stage sections (not just bare tables).
set -euo pipefail

LOG_DIR="${LOG_DIR:-logs}"
OUT="$LOG_DIR/flow-execution-report.html"
DEST_DIR="$LOG_DIR/flow-report"

MAT="$LOG_DIR/flow-execution-report-mat.html"
REG="$LOG_DIR/flow-execution-report-regression.html"

if [ ! -f "$MAT" ] && [ ! -f "$REG" ]; then
  echo "No flow reports to merge in $LOG_DIR"
  exit 0
fi

{
  echo '<!DOCTYPE html><html><head><meta charset="utf-8"/>'
  echo '<title>Flow Execution Report (combined)</title></head><body>'
  echo '<h1><b>Flow Execution Report — combined</b></h1>'

  if [ -f "$MAT" ]; then
    echo '<h2><b>MAT</b></h2>'
    sed -n '/<table /,/<\/table>/p' "$MAT" | sed -n '1,/<\/table>/p'
  fi

  if [ -f "$REG" ]; then
    echo '<h2><b>Regression</b></h2>'
    sed -n '/<table /,/<\/table>/p' "$REG" | sed -n '1,/<\/table>/p'
  fi

  echo '</body></html>'
} > "$OUT"

# Keep publishHTML bundle in sync
mkdir -p "$DEST_DIR/flows"
cp "$OUT" "$DEST_DIR/flow-execution-report.html"
if [ -d "$LOG_DIR/flows" ]; then
  cp -r "$LOG_DIR/flows/." "$DEST_DIR/flows/" 2>/dev/null || true
fi

echo "Wrote $OUT and $DEST_DIR/flow-execution-report.html"

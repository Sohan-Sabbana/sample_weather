#!/usr/bin/env bash
# Merges per-stage flow-execution-report-*.html into one combined report for Jenkins.
set -euo pipefail

LOG_DIR="${LOG_DIR:-logs}"
OUT="$LOG_DIR/flow-execution-report.html"

MAT="$LOG_DIR/flow-execution-report-mat.html"
REG="$LOG_DIR/flow-execution-report-regression.html"

if [ ! -f "$MAT" ] && [ ! -f "$REG" ]; then
  echo "No flow reports to merge in $LOG_DIR"
  exit 0
fi

{
  echo '<!DOCTYPE html><html><head><meta charset="utf-8"/>'
  echo '<title>Flow Execution Report (combined)</title>'
  echo '<style>body{font-family:system-ui,sans-serif;margin:1.5rem}'
  echo 'h2{margin-top:2rem;border-top:1px solid #ccc;padding-top:1rem}</style></head><body>'
  echo '<h1>Flow Execution Report — combined</h1>'
  if [ -f "$MAT" ]; then
    echo '<h2>MAT</h2>'
    sed -n '/<table>/,/<\/table>/p' "$MAT"
  fi
  if [ -f "$REG" ]; then
    echo '<h2>Regression</h2>'
    sed -n '/<table>/,/<\/table>/p' "$REG"
  fi
  echo '</body></html>'
} > "$OUT"

echo "Wrote $OUT"

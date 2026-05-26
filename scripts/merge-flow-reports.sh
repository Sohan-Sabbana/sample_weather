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

read -r -d '' CSS <<'EOF' || true
body{font-family:Segoe UI,system-ui,sans-serif;margin:1.5rem 2rem;color:#1a1a1a}
h1{font-size:1.5rem;border-bottom:2px solid #333;padding-bottom:.5rem}
h2{margin-top:2rem;border-bottom:1px solid #666;padding-bottom:.35rem}
.flow-table{border-collapse:collapse;width:100%;margin-top:1rem;border:2px solid #333}
.flow-table th,.flow-table td{border:1px solid #333;padding:.6rem .85rem;text-align:left;vertical-align:top}
.flow-table th{background:#e8e8e8;font-weight:600}
.flow-table tr.pass td{background:#f6fff8}
.flow-table tr.fail td{background:#fff5f5}
.flow-table tr.skip td{background:#fffef0}
.flow-link{font-weight:600;color:#0b5fff}
.trace code{font-size:.8rem;word-break:break-all}
.meta{color:#444;font-size:.9rem}
EOF

{
  echo '<!DOCTYPE html><html><head><meta charset="utf-8"/>'
  echo '<title>Flow Execution Report (combined)</title>'
  echo "<style>${CSS}</style></head><body>"
  echo '<h1>Flow Execution Report — combined</h1>'
  if [ -f "$MAT" ]; then
    echo '<h2>MAT</h2>'
    sed -n '/<table class="flow-table">/,/<\/table>/p' "$MAT"
  fi
  if [ -f "$REG" ]; then
    echo '<h2>Regression</h2>'
    sed -n '/<table class="flow-table">/,/<\/table>/p' "$REG"
  fi
  echo '</body></html>'
} > "$OUT"

echo "Wrote $OUT"

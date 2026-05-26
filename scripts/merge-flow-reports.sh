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
body{font-family:Segoe UI,system-ui,sans-serif;margin:1.5rem 2rem;color:#1a1a1a;background:#fafafa}
h1{font-size:1.5rem;border-bottom:3px solid #222;padding-bottom:.5rem}
h2{margin-top:2rem;border-bottom:2px solid #666;padding-bottom:.35rem}
.flow-table{border-collapse:collapse;width:100%;margin-top:1rem;border:3px solid #222}
.flow-table th,.flow-table td{border:2px solid #444;padding:.65rem .9rem;text-align:center;vertical-align:middle}
.flow-table th{background:#d4d4d4;font-weight:700}
.flow-table td.flow-name,.flow-table td.trace,.flow-table td.actions{text-align:left}
.result-block{font-weight:700;font-size:1.05rem}
.block-pass{background:#16a34a!important;color:#fff!important;border:2px solid #14532d!important}
.block-fail{background:#dc2626!important;color:#fff!important;border:2px solid #7f1d1d!important}
.block-skip{background:#ca8a04!important;color:#fff!important;border:2px solid #713f12!important}
.block-empty{background:#f3f4f6;color:#9ca3af}
.badge{display:inline-block;padding:.25rem .65rem;border-radius:4px;font-size:.75rem;font-weight:700;border:2px solid}
.badge-pass{background:#16a34a;color:#fff}.badge-fail{background:#dc2626;color:#fff}.badge-skip{background:#ca8a04;color:#fff}
.flow-link,.action-link{color:#1d4ed8;text-decoration:underline}
.kibana-link{color:#7c3aed;font-weight:600}
.trace code{font-size:.78rem;word-break:break-all}
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

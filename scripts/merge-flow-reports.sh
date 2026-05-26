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

BODY_STYLE='font-family:Segoe UI,sans-serif;margin:24px;color:#111;background:#fafafa'
H1_STYLE='border-bottom:3px solid #222;padding-bottom:8px'
H2_STYLE='margin-top:28px;border-bottom:2px solid #666;padding-bottom:6px'

{
  echo '<!DOCTYPE html><html><head><meta charset="utf-8"/>'
  echo '<title>Flow Execution Report (combined)</title></head>'
  echo "<body style=\"${BODY_STYLE}\">"
  echo "<h1 style=\"${H1_STYLE}\">Flow Execution Report — combined</h1>"

  if [ -f "$MAT" ]; then
    echo "<h2 style=\"${H2_STYLE}\">MAT</h2>"
    # Table rows include inline pass/fail cell styles from FlowExecutionListener
    sed -n '/<table style=/,/<\/table>/p' "$MAT"
  fi

  if [ -f "$REG" ]; then
    echo "<h2 style=\"${H2_STYLE}\">Regression</h2>"
    sed -n '/<table style=/,/<\/table>/p' "$REG"
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

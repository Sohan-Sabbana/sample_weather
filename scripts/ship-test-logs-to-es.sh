#!/usr/bin/env bash
# Pushes a newline-delimited JSON log file (LogstashEncoder output from TestNG)
# into Elasticsearch using the _bulk API.
#
# Args:
#   $1 = path to the .json log file produced by the test JVM
#   $2 = suite tag (e.g. "mat", "regression") — used only if missing from the doc
#
# Env:
#   ES_URL          default http://host.docker.internal:9200
#   BUILD_NUMBER    Jenkins build number (used only if missing from the doc)

set -euo pipefail

LOG_FILE="${1:?log file path required}"
SUITE="${2:-tests}"
ES_URL="${ES_URL:-http://host.docker.internal:9200}"
BUILD="${BUILD_NUMBER:-local}"
INDEX="weather-logs-test-$(date -u +%Y.%m.%d)"

if [ ! -s "$LOG_FILE" ]; then
  echo "No log file or empty: $LOG_FILE"
  exit 0
fi

TMP="$(mktemp)"
RESP="$(mktemp)"
trap 'rm -f "$TMP" "$RESP"' EXIT

# Merge fields with Python so we never duplicate keys (ES rejects duplicate JSON fields).
python3 - "$LOG_FILE" "$SUITE" "$BUILD" > "$TMP" <<'PY'
import json
import sys

log_file, suite, build = sys.argv[1:4]
count = 0
with open(log_file, encoding="utf-8") as fh:
    for raw in fh:
        line = raw.strip()
        if not line:
            continue
        doc = json.loads(line)
        doc.setdefault("source", "tests")
        doc.setdefault("suite", suite)
        doc.setdefault("build", build)
        if "message" in doc:
            doc.setdefault("log", doc["message"])
        print('{"index":{}}')
        print(json.dumps(doc, separators=(",", ":")))
        count += 1
if count == 0:
    sys.exit(2)
print(f"Prepared {count} documents", file=sys.stderr)
PY

echo "Shipping documents from $LOG_FILE -> $ES_URL/$INDEX"

HTTP_CODE=$(curl -s -o "$RESP" -w "%{http_code}" \
  -H "Content-Type: application/x-ndjson" \
  -XPOST "$ES_URL/$INDEX/_bulk?refresh=true" \
  --data-binary "@$TMP")

if [ "$HTTP_CODE" != "200" ]; then
  echo "Bulk upload failed with HTTP $HTTP_CODE:"
  head -c 2000 "$RESP"
  echo
  exit 1
fi

if ! python3 - "$RESP" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as fh:
    body = json.load(fh)
if body.get("errors"):
  items = body.get("items") or []
  failed = [i for i in items if i.get("index", {}).get("error")]
  print(f"Bulk reported errors ({len(failed)} failed documents).", file=sys.stderr)
  if failed:
    print(json.dumps(failed[0], indent=2)[:1500], file=sys.stderr)
  sys.exit(1)
indexed = sum(1 for i in body.get("items", []) if i.get("index", {}).get("result") in ("created", "updated"))
print(f"Bulk upload OK ({indexed} indexed).")
PY
then
  head -c 2000 "$RESP"
  echo
  exit 1
fi

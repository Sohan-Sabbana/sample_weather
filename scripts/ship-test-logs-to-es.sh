#!/usr/bin/env bash
# Pushes a newline-delimited JSON log file (the LogstashEncoder output that the
# TestNG suite produced) into Elasticsearch using the _bulk API.
#
# Args:
#   $1 = path to the .json log file produced by the test JVM
#   $2 = suite tag (e.g. "mat", "regression")
#
# Env required:
#   ES_URL          e.g. http://host.docker.internal:30200
#   BUILD_NUMBER    Jenkins build number (used to enrich each doc)

set -euo pipefail

LOG_FILE="${1:?log file path required}"
SUITE="${2:-tests}"
ES_URL="${ES_URL:-http://host.docker.internal:30200}"
BUILD="${BUILD_NUMBER:-local}"
INDEX="weather-logs-test-$(date -u +%Y.%m.%d)"

if [ ! -s "$LOG_FILE" ]; then
  echo "No log file or empty: $LOG_FILE"; exit 0
fi

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

# Build NDJSON bulk body: alternate action line + source line.
# Inject source=tests, suite=<mat|regression>, build=$BUILD into every doc.
awk -v suite="$SUITE" -v build="$BUILD" '
  {
    print "{\"index\":{}}"
    # Insert extra fields just before the closing brace
    sub(/}$/, ",\"source\":\"tests\",\"suite\":\"" suite "\",\"build\":\"" build "\"}")
    print
  }
' "$LOG_FILE" > "$TMP"

LINES=$(wc -l < "$TMP")
echo "Shipping $((LINES / 2)) log events from $LOG_FILE -> $ES_URL/$INDEX"

HTTP_CODE=$(curl -s -o /tmp/es-bulk-resp -w "%{http_code}" \
  -H "Content-Type: application/x-ndjson" \
  -XPOST "$ES_URL/$INDEX/_bulk" \
  --data-binary "@$TMP")

if [ "$HTTP_CODE" != "200" ]; then
  echo "Bulk upload failed with HTTP $HTTP_CODE:"
  head -c 1000 /tmp/es-bulk-resp; echo
  exit 1
fi

echo "Bulk upload OK."

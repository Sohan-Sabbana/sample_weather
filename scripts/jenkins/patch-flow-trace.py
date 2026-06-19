#!/usr/bin/env python3
import json
import sys

path = sys.argv[1]
trace_id = sys.argv[2]
flow_name = sys.argv[3]

with open(path, encoding="utf-8") as f:
    data = json.load(f)

data.setdefault("flows", []).append(
    {
        "flowName": flow_name,
        "status": "FAIL",
        "traceId": trace_id,
        "stage": "regression",
    }
)

with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, indent=2)

print(f"patched {path} with traceId={trace_id}")

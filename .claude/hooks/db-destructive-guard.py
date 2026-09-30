#!/usr/bin/env python3
"""PreToolUse hook (Bash): ask before running anything that drops or wipes the banking database
(db/ddl/02_drop_tables.sql, db/dml/03_reset_banking_data.sql, DROP DATABASE/TABLE, TRUNCATE).
Returns permissionDecision "ask" so the user confirms; all other commands pass through untouched."""
import json
import re
import sys

try:
    payload = json.load(sys.stdin)
except Exception:
    sys.exit(0)

command = (payload.get("tool_input") or {}).get("command") or ""
patterns = [
    r"02_drop_tables", r"03_reset_banking_data",
    r"\bdrop\s+(database|schema|table)\b", r"\btruncate\s+table\b",
]
if any(re.search(p, command, re.IGNORECASE) for p in patterns):
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "ask",
        "permissionDecisionReason": "This command drops or wipes banking tables in db_example (destructive, not reversible). Confirm before running.",
    }}))

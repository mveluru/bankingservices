#!/usr/bin/env python3
"""PreToolUse hook (Bash): ask before running anything that drops, truncates, alters (drop column/etc.) or
deletes rows from a BANKING table in db_example, or that drops the whole database / runs the repo's
drop and reset scripts. Statements that touch only non-banking tables (events, employee, inventory, user, ...)
pass through untouched. Returns permissionDecision "ask" so the user confirms.

Banking tables guarded (names must match db/ddl/01_create_tables.sql and the JPA entities):
  accounts, customers, account_transactions, withdrawal_history, bank_locations, bank_location_services,
  bank_employees, bank_employee_credentials, customer_credentials
"""
import json
import re
import sys

BANKING_TABLES = [
    "accounts",
    "customers",
    "account_transactions",
    "withdrawal_history",
    "bank_locations",
    "bank_location_services",
    "bank_employees",
    "bank_employee_credentials",
    "customer_credentials",
]

# Deleting these loses real records or breaks the links other tables hold by id; close accounts instead of deleting them.
NEVER_DELETE = {"accounts", "customers", "bank_employees", "bank_employee_credentials", "customer_credentials"}

# Scripts in this repo that are destructive for banking tables (02 also drops `events`).
SCRIPTS = [
    ("02_drop_tables", "db/ddl/02_drop_tables.sql (drops ALL tables, including the banking tables)"),
    ("03_reset_banking_data", "db/dml/03_reset_banking_data.sql (deletes ALL banking rows)"),
]


def table_name(token):
    """`db_example`.`accounts` / db_example.accounts / `accounts` -> accounts."""
    return token.replace("`", "").replace('"', "").split(".")[-1].strip().lower()


def banking_in(names):
    return [t for t in (table_name(n) for n in names) if t in BANKING_TABLES]


def find_hits(command):
    hits = []  # (kind, tables)

    for key, label in SCRIPTS:
        if re.search(key, command, re.IGNORECASE):
            hits.append((f"runs {label}", BANKING_TABLES))

    if re.search(r"\bdrop\s+(database|schema)\b", command, re.IGNORECASE):
        hits.append(("DROP DATABASE/SCHEMA (removes every banking table)", BANKING_TABLES))

    # DROP TABLE [IF EXISTS] a, b, c  (a statement may list several tables)
    for m in re.finditer(r"\bdrop\s+table\s+(?:if\s+exists\s+)?([^;\"'\n]*)", command, re.IGNORECASE):
        tables = banking_in(re.split(r"[,\s]+", m.group(1).strip()))
        if tables:
            hits.append(("DROP TABLE", tables))

    for m in re.finditer(r"\btruncate\s+(?:table\s+)?([`\"\w.]+)", command, re.IGNORECASE):
        tables = banking_in([m.group(1)])
        if tables:
            hits.append(("TRUNCATE", tables))

    for m in re.finditer(r"\bdelete\s+from\s+([`\"\w.]+)", command, re.IGNORECASE):
        tables = banking_in([m.group(1)])
        if tables:
            hits.append(("DELETE FROM (banking table data)", tables))

    # DDL that removes/changes structure: ALTER TABLE <banking> DROP/MODIFY/CHANGE/RENAME
    for m in re.finditer(r"\balter\s+table\s+([`\"\w.]+)\s+(drop|modify|change|rename)\b", command, re.IGNORECASE):
        tables = banking_in([m.group(1)])
        if tables:
            hits.append((f"ALTER TABLE ... {m.group(2).upper()}", tables))

    return hits


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return
    command = (payload.get("tool_input") or {}).get("command") or ""
    hits = find_hits(command)
    if not hits:
        return

    lines = []
    for kind, tables in hits:
        uniq = list(dict.fromkeys(tables))
        lines.append(f"- {kind}: {', '.join(uniq)}")
    touched = {t for _, tables in hits for t in tables}
    advice = ""
    if touched & NEVER_DELETE:
        advice = ("\nPrefer ending an account over deleting it: POST /v1/api/accounts/{n}/close stamps closedDate and keeps the row "
                  "(logins reference customers/employees by plain id, so deleting or renumbering rows orphans them)")
    reason = (
        "This command would drop/wipe/alter BANKING tables in db_example (not reversible):\n"
        + "\n".join(lines)
        + "\nGuarded banking tables: " + ", ".join(BANKING_TABLES)
        + advice
        + ".\nTake/confirm a backup first (see ~/db_backups), then confirm to run."
    )
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "ask",
        "permissionDecisionReason": reason,
    }}))


main()

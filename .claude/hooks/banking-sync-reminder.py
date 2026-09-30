#!/usr/bin/env python3
"""PostToolUse hook (Edit|Write|MultiEdit): after a banking source file changes, remind Claude which
companion files must change in the same commit (db/ scripts, OpenAPI, README, tests), and validate the
OpenAPI spec when it was the file edited. Output goes to Claude as additionalContext; never blocks."""
import json
import os
import shutil
import subprocess
import sys

try:
    payload = json.load(sys.stdin)
except Exception:
    sys.exit(0)

path = (payload.get("tool_input") or {}).get("file_path") or ""
project = os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or ""
rel = os.path.relpath(path, project) if project and path.startswith(project) else path
rel = rel.replace(os.sep, "/")

notes = []


def has(*fragments):
    return any(f in rel for f in fragments)


if has("banking/entity/") or rel.endswith("AccountDataSeeder.java") or rel.endswith("AccountStatusDemoSeeder.java") \
        or rel.endswith("BankLocationDataSeeder.java") or rel.endswith("domain/AccountStatus.java"):
    notes.append(
        "Entity/seeder/status-enum change: update db/ddl/01_create_tables.sql, db/data/* (seed mirrors), db/dml/*, db/README.md. "
        "ddl-auto:update never alters an existing enum column, so widening an enum also needs a db/ddl/NN_*_migration.sql "
        "applied to live databases. Keep seed dates relative and account numbers CH-/SV- + 10 digits.")

if has("banking/contoller/", "banking/request/", "banking/bff/controller/", "banking/bff/dto/") \
        or rel.endswith("BankingExceptionHandler.java"):
    notes.append(
        "API surface change: update src/main/resources/static/openapi/banking-openapi.yaml (paths, schemas, required, status codes, "
        "text/plain vs JSON errors), the README endpoint table/curl samples, and BankingGatewayConfig.BANKING_URL_PATTERNS for any new path.")

if has("banking/domain/AccountStatusView.java") or has("banking/service/AccountSuspensionService.java") \
        or has("banking/service/ClientAccountService.java") or has("banking/service/AccountStatusStatementService.java"):
    notes.append(
        "If this changes a field AccountStatusView shows, the mutation must carry "
        "@CacheEvict(ACCOUNT_SEARCH_CACHE, allEntries = true) and AccountSearchCachingTest must cover it.")

if has("banking/repository/AccountRepository.java", "banking/service/AccountSuspensionService.java", "banking/entity/AccountEntity.java"):
    notes.append(
        "Account lifecycle invariant: a SUSPENDED account must reject every withdraw/deposit (status and suspended flag written together). "
        "Keep .claude/rules/banking/account-lifecycle.md and the AccountRepositoryTest suspension tests in step.")

if rel.startswith("src/main/java/org/bee/banking/") and "/bff/" not in rel and not rel.endswith("BankingMessages.java"):
    notes.append("Add new user-visible/log strings to BankingMessages, not as literals. Add or update the matching test.")

if rel.endswith("banking-openapi.yaml"):
    validator = shutil.which("openapi-spec-validator")
    cmd = [validator, path] if validator else [sys.executable, "-m", "openapi_spec_validator", path]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=25)
        out = (result.stdout + result.stderr).strip()
        if result.returncode == 0:
            notes.append("OpenAPI validation: OK.")
        elif "No module named" in out:
            notes.append("OpenAPI spec edited but openapi-spec-validator isn't installed "
                         "(pip install openapi-spec-validator); validate it before committing. "
                         "Quote YAML plain scalars containing ': ' and flow-sequence items containing ','.")
        else:
            notes.append("OpenAPI validation FAILED, fix before continuing:\n" + out[-800:])
    except Exception as exc:  # validator missing/timeout must never break the session
        notes.append(f"OpenAPI spec edited; validator could not run ({exc}).")

if not notes:
    sys.exit(0)

print(json.dumps({"hookSpecificOutput": {
    "hookEventName": "PostToolUse",
    "additionalContext": f"[banking sync reminder for {rel}]\n- " + "\n- ".join(notes),
}}))

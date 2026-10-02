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


SEEDERS = ("AccountDataSeeder.java", "AccountStatusDemoSeeder.java", "BankLocationDataSeeder.java",
           "EmployeeDataSeeder.java", "EmployeeCredentialSeeder.java", "CustomerCredentialSeeder.java")
DB_ENUMS = ("domain/AccountStatus.java", "domain/LoginStatus.java", "domain/EmployeeRole.java",
            "domain/EmployeeStatus.java", "domain/LocationType.java", "domain/TransactionType.java")

if has("banking/entity/") or rel.endswith(SEEDERS) or rel.endswith(DB_ENUMS):
    notes.append(
        "Entity/seeder/enum change: update db/ddl/01_create_tables.sql, db/data/* (seed mirrors: 01/05 accounts, 04 locations, "
        "07 employees, 08 employee logins, 09 customer logins), db/dml/* (incl. 03_reset_banking_data.sql when a table is added), db/README.md. "
        "ddl-auto:update never alters an existing enum column, so widening an enum (AccountStatus, LoginStatus, EmployeeRole...) also needs "
        "an idempotent db/ddl/NN_*_migration.sql applied to live databases (new columns on existing tables get one too: 04-06 are the pattern). "
        "Keep seed dates relative and account numbers CH-/SV- + 10 digits. Demo credentials are DEMO ONLY and stored as BCrypt hashes. "
        "A NEW TABLE (new *Entity) needs the whole set in db/: DDL (ddl/01 + the DROP in ddl/02 + an idempotent ddl/NN migration), DML (a dml/ script + "
        "the delete/AUTO_INCREMENT in dml/03_reset_banking_data.sql) and DATA (a data/ backup snapshot of the table's current rows as re-runnable "
        "INSERT ... ON DUPLICATE KEY UPDATE, or a seeder mirror), then list them in db/README.md ('Adding a table'). doc-sync.py blocks the turn without them.")

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
        "Suspend/update-suspension/reactivate are staff-only (StaffController; no customer or portal route): do not re-add one. "
        "Keep .claude/rules/banking/account-lifecycle.md and the AccountRepositoryTest suspension tests in step.")

if has("banking/service/Employee", "banking/service/Staff", "banking/service/LoginSupport", "banking/service/CustomerCredential",
       "banking/contoller/StaffController", "banking/contoller/LoginController", "banking/domain/Employee", "banking/domain/Login",
       "banking/domain/CustomerCredential", "banking/domain/TransactionHandler"):
    notes.append(
        "Employee/login rules: privileges derive from EmployeeRole (not a column); only an ACTIVE employee with an ACTIVE login may act "
        "(EmployeeService.requirePrivilege), and customer-initiated deposit/withdraw is gated by CustomerCredentialService.requireActiveLoginIfPresent. "
        "Keep .claude/rules/banking/employees-and-logins.md, the OpenAPI Staff/Login tags + enums (EmployeeRole, EmployeePrivilege, LoginStatus), "
        "the README staff rows and EmployeeServiceTest/StaffControllerTest/*Credential*Test in step. Staff endpoints require the employee JWT "
        "(StaffAuthenticationFilter, token subject = acting employee, permissions reloaded each call); customer account/portal endpoints require the "
        "customer JWT (CustomerAuthenticationFilter, login re-checked each call) and CustomerAccessService keeps a customer to their own accounts "
        "(a new customer-facing handler must call it, and a new list/search must be scoped by customerId); X-Customer-Id is only a rate-limit key.")

if has("service/JwtService", "service/LoginService", "rules/JwtProperties", "domain/IssuedToken", "domain/TokenClaims", "LoginResponse"):
    notes.append(
        "JWT: the signing secret comes only from BANKING_JWT_SECRET (>= 32 chars, never committed, no real secret in tests); HMAC only and keep "
        "alg:none refused; no personal data or passwords in claims; never log a token; login responses stay Cache-Control: no-store and no token is "
        "issued unless verify succeeded. A token is not enforced anywhere yet: if you add a bearer filter, re-check login/employee status, put it in front "
        "of the rate limiter, and update employees-and-logins.md, security.md, OpenAPI (securitySchemes) and the README. Keep JwtServiceTest in step.")

if has("Credential", "LoginRequest", "LoginController", "BankingRequestLoggingFilter", "LoginSupport"):
    notes.append(
        "Credentials: never log, return or put in an exception/URL a password or hash; store only BCrypt hashes; usernames lowercase, passwords exactly "
        "8 digits (the lockout is the real protection, see banking.*-login.*). Any new credential field (passwords, security "
        "answers) must be masked in BankingRequestLoggingFilter.mask (with a test), and the domain/request toString must exclude it. Security answers: "
        "normalised, BCrypt-hashed, slots replaced in place (never deleted); a password reset must never undo an administrator's login status, and a "
        "password change must keep invalidating older tokens.")

if has("gateway/BankingGatewayConfig.java"):
    notes.append(
        "BANKING_URL_PATTERNS changed: mirror it in README (gateway section), CLAUDE.md (gateway section) and .claude/rules/banking/gateway-layer.md.")

if rel.startswith("src/main/java/org/brite/banking/") and "/bff/" not in rel and not rel.endswith("BankingMessages.java"):
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

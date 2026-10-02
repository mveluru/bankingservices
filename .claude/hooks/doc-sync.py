#!/usr/bin/env python3
"""Documentation-sync enforcer. One script, two events:

  UserPromptSubmit  records a snapshot of the working tree (content hash of every modified/untracked/deleted
                    file) at the start of the turn. Prints nothing.
  Stop              compares the working tree with that snapshot. If this turn changed functionality (source,
                    config, pom, db scripts, hooks, skills) but did not update the documentation that describes
                    it, the stop is BLOCKED and Claude is told exactly which docs to update. At most twice per
                    turn, so it can never loop.

Why a snapshot and not "git diff": edits made through Bash (sed, python heredocs) never reach an Edit/Write hook, and
a dirty tree left by an earlier turn must not nag forever. Files that were dirty before the turn only count if they
changed again during it; committing during a turn is not a change.

Escape hatch for changes that genuinely need no documentation (a pure refactor, a typo): end the final message with a
line starting `Docs: no update needed` and a reason. Test files never need documentation, and local-only files
(CLAUDE.local.md, settings.local.json) are ignored.

What counts (paths relative to the project root):
  functionality  src/main/** (code, application*.yml, swagger-ui), pom.xml, db/** (scripts, not db/README.md),
                 .claude/hooks/**, .claude/settings.json, .claude/skills/**
  README.md          <- any code/config/pom change
  .claude/CLAUDE.md or .claude/rules/**   <- any functionality change
  banking-openapi.yaml  <- controllers, requests, exceptions, response/domain DTOs, bff, gateway config
  db/**               <- entities and seeders (a script or db/README.md must change with them)
  NEW table           <- a new *Entity.java (untracked) needs ALL of: ddl/01_create_tables.sql, ddl/02_drop_tables.sql, an idempotent
                         ddl/NN_*migration*.sql, a dml/ script, a data/ script (a backup/snapshot of the table's rows, or a seeder
                         mirror) and db/README.md
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

MAX_BLOCKS_PER_TURN = 2
ESCAPE = re.compile(r"(?im)^\W*docs?:\s*no\s+(documentation\s+)?(update|change)s?\s+(needed|required)")
OPENAPI = "src/main/resources/static/openapi/banking-openapi.yaml"
IGNORED_PARTS = ("__pycache__/", ".idea/")
IGNORED_FILES = (".claude/settings.local.json", "CLAUDE.local.md")


def load_payload():
    try:
        return json.load(sys.stdin)
    except Exception:
        return {}


def project_dir(payload):
    return Path(os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or os.getcwd())


def state_file(payload):
    directory = Path(tempfile.gettempdir()) / "claude-doc-sync"
    directory.mkdir(parents=True, exist_ok=True)
    return directory / f"{payload.get('session_id') or 'nosession'}.json"


def snapshot(project):
    """{relative path: sha1 of content, or 'DELETED'} for every modified, untracked or deleted file."""
    try:
        out = subprocess.run(["git", "-C", str(project), "ls-files", "-m", "-o", "-d", "--exclude-standard", "-z"],
                             capture_output=True, timeout=20).stdout
    except Exception:
        return None
    result = {}
    for raw in out.split(b"\0"):
        if not raw:
            continue
        rel = raw.decode("utf-8", "replace")
        if rel in IGNORED_FILES or any(part in rel for part in IGNORED_PARTS):
            continue
        path = project / rel
        try:
            result[rel] = hashlib.sha1(path.read_bytes()).hexdigest() if path.is_file() else "DELETED"
        except OSError:
            result[rel] = "UNREADABLE"
    return result


def is_functional(rel):
    return (rel.startswith("src/main/") and rel != OPENAPI) or rel == "pom.xml" \
        or (rel.startswith("db/") and rel != "db/README.md") \
        or rel.startswith(".claude/hooks/") or rel == ".claude/settings.json" or rel.startswith(".claude/skills/")


def is_code_or_config(rel):
    return (rel.startswith("src/main/java/") or rel.startswith("src/main/resources/application") or rel == "pom.xml")


def touches_api(rel):
    return rel.startswith("src/main/java/") and any(f in rel for f in (
        "/contoller/", "/request/", "/exception/", "/bff/controller/", "/bff/dto/", "/gateway/BankingGatewayConfig",
        "LoginResponse", "/domain/Employee", "/domain/AuthenticatedCustomer", "/domain/LoginStatusView", "/domain/Account",
        "/domain/BankLocations", "/domain/BankStatement", "/domain/BulkClose", "/domain/DepositForm", "/domain/Customer.java"))


def touches_db(rel):
    return rel.startswith("src/main/java/") and ("/entity/" in rel or rel.endswith("Seeder.java"))


def is_new_entity_candidate(rel):
    """A JPA entity class, i.e. a table (embeddables and everything else under entity/ don't count)."""
    return rel.startswith("src/main/java/") and "/entity/" in rel and rel.endswith("Entity.java")


MIGRATION = re.compile(r"^db/ddl/\d\d_.*migration.*\.sql$")


def is_doc_claude(rel):
    return rel == ".claude/CLAUDE.md" or rel.startswith(".claude/rules/")


def unmet(changed, new_entities=()):
    """List of (requirement text, [triggering files]) not satisfied by the docs in `changed`.
    `new_entities`: entity classes created this turn (new tables), which need the full set of db/ files."""
    functional = sorted(p for p in changed if is_functional(p))
    if not functional:
        return []
    docs_readme = "README.md" in changed
    docs_claude = any(is_doc_claude(p) for p in changed)
    docs_openapi = OPENAPI in changed
    docs_db = any(p.startswith("db/") for p in changed)

    problems = []
    code = [p for p in functional if is_code_or_config(p)]
    if code and not docs_readme:
        problems.append(("README.md (features, endpoint table, configuration, data model, test table, curl samples)", code))
    if not docs_claude:
        problems.append((".claude/CLAUDE.md and/or the matching .claude/rules/banking/*.md (architecture, rules, hooks, tests)", functional))
    api = [p for p in functional if touches_api(p)]
    if api and not docs_openapi:
        problems.append((f"{OPENAPI} (paths, schemas, status codes, enums) and validate it", api))
    dbs = [p for p in functional if touches_db(p)]
    if dbs and not docs_db:
        problems.append(("db/ (ddl/01_create_tables.sql, data/*, a migration for new columns/enums, db/README.md)", dbs))
    new_entities = sorted(new_entities)
    if new_entities:
        table_needs = (
            ("db/ddl/01_create_tables.sql (the new table and its unique indexes)", "db/ddl/01_create_tables.sql" in changed),
            ("db/ddl/02_drop_tables.sql (DROP TABLE IF EXISTS for it)", "db/ddl/02_drop_tables.sql" in changed),
            ("an idempotent db/ddl/NN_<name>_migration.sql (CREATE TABLE IF NOT EXISTS + guarded index) for existing databases",
             any(MIGRATION.match(p) for p in changed)),
            ("db/dml/ (a script with the hand-run statements for the table, and the delete/AUTO_INCREMENT in 03_reset_banking_data.sql)",
             any(p.startswith("db/dml/") for p in changed)),
            ("db/data/ (a DATA BACKUP: a snapshot of the table's current rows as re-runnable INSERT ... ON DUPLICATE KEY UPDATE, or a mirror of the seeder that fills it)",
             any(p.startswith("db/data/") for p in changed)),
            ("db/README.md (list the new files; see 'Adding a table')", "db/README.md" in changed),
        )
        for need, met in table_needs:
            if not met:
                problems.append((f"NEW TABLE: {need}", new_entities))
    return problems


def untracked(project):
    try:
        out = subprocess.run(["git", "-C", str(project), "ls-files", "-o", "--exclude-standard", "-z"],
                             capture_output=True, timeout=20).stdout
    except Exception:
        return set()
    return {raw.decode("utf-8", "replace") for raw in out.split(b"\0") if raw}


def escaped(payload):
    path = payload.get("transcript_path")
    if not path or not os.path.isfile(path):
        return False
    last = ""
    try:
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                try:
                    entry = json.loads(line)
                except ValueError:
                    continue
                message = entry.get("message") or {}
                if entry.get("type") == "assistant" or message.get("role") == "assistant":
                    content = message.get("content")
                    text = content if isinstance(content, str) else "\n".join(
                        c.get("text", "") for c in (content or []) if isinstance(c, dict) and c.get("type") == "text")
                    if text.strip():
                        last = text
    except OSError:
        return False
    return bool(ESCAPE.search(last))


def main():
    payload = load_payload()
    event = payload.get("hook_event_name")
    project = project_dir(payload)
    state = state_file(payload)

    if event == "UserPromptSubmit":
        snap = snapshot(project)
        if snap is not None:
            state.write_text(json.dumps({"baseline": snap, "blocks": 0}))
        return

    if event != "Stop":
        return
    try:
        saved = json.loads(state.read_text())
    except Exception:
        return  # no baseline for this turn (hook added mid-session): nothing to compare
    current = snapshot(project)
    if current is None:
        return
    baseline = saved.get("baseline", {})
    changed = {p for p, h in current.items() if baseline.get(p) != h}
    new_entities = {p for p in changed if is_new_entity_candidate(p)} & untracked(project)
    problems = unmet(changed, new_entities)
    if not problems or saved.get("blocks", 0) >= MAX_BLOCKS_PER_TURN or escaped(payload):
        return

    saved["blocks"] = saved.get("blocks", 0) + 1
    state.write_text(json.dumps(saved))
    lines = []
    for need, files in problems:
        shown = ", ".join(files[:6]) + (f" (+{len(files) - 6} more)" if len(files) > 6 else "")
        lines.append(f"- update {need}\n    because of: {shown}")
    reason = (
        "Documentation is out of date for this turn's functional changes. Update ALL documentation that describes them, "
        "then finish:\n" + "\n".join(lines)
        + "\nAlso check the other docs the change touches (README sections, db/README.md, OpenAPI examples, the checklist skill). "
        "If a change genuinely needs no documentation (pure refactor, typo), end your final message with a line starting "
        "'Docs: no update needed' followed by the reason."
    )
    print(json.dumps({"decision": "block", "reason": reason}))


main()

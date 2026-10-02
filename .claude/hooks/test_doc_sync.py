#!/usr/bin/env python3
"""Self-test for doc-sync.py: builds a throwaway git repo, drives the hook with UserPromptSubmit/Stop payloads and
checks when it blocks. Run: python3 -m unittest discover -s .claude/hooks -p 'test_*.py'"""
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

HOOK = Path(__file__).with_name("doc-sync.py")
OPENAPI = "src/main/resources/static/openapi/banking-openapi.yaml"
SERVICE = "src/main/java/org/brite/banking/service/FooService.java"
CONTROLLER = "src/main/java/org/brite/banking/contoller/FooController.java"
ENTITY = "src/main/java/org/brite/banking/entity/FooEntity.java"
NEW_ENTITY = "src/main/java/org/brite/banking/entity/BarEntity.java"
TEST = "src/test/java/org/brite/banking/service/FooServiceTest.java"
BASE_FILES = ["README.md", ".claude/CLAUDE.md", ".claude/rules/banking/design.md", OPENAPI, "db/README.md",
              "db/ddl/01_create_tables.sql", SERVICE, CONTROLLER, ENTITY, TEST, "pom.xml",
              "src/main/resources/application.yml", ".claude/hooks/x.py"]


class DocSyncTest(unittest.TestCase):
    def setUp(self):
        self.repo = Path(tempfile.mkdtemp())
        self.session = f"test-{self.repo.name}"
        for rel in BASE_FILES:
            self.write(rel, "v1\n")
        self.git("init", "-q")
        self.git("-c", "user.email=t@t", "-c", "user.name=t", "add", "-A")
        self.git("-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qm", "base")

    def tearDown(self):
        shutil.rmtree(self.repo, ignore_errors=True)
        shutil.rmtree(Path(tempfile.gettempdir()) / "claude-doc-sync" / f"{self.session}.json", ignore_errors=True)
        try:
            (Path(tempfile.gettempdir()) / "claude-doc-sync" / f"{self.session}.json").unlink()
        except OSError:
            pass

    def git(self, *args):
        subprocess.run(["git", "-C", str(self.repo), *args], check=True, capture_output=True)

    def write(self, rel, text):
        path = self.repo / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def run_hook(self, event, **extra):
        payload = {"hook_event_name": event, "session_id": self.session, "cwd": str(self.repo), **extra}
        env = {**os.environ, "CLAUDE_PROJECT_DIR": str(self.repo)}
        out = subprocess.run(["python3", str(HOOK)], input=json.dumps(payload), capture_output=True, text=True, env=env).stdout
        return json.loads(out) if out.strip() else None

    def start_turn(self):
        self.assertIsNone(self.run_hook("UserPromptSubmit"))

    def stop(self, **extra):
        return self.run_hook("Stop", **extra)

    def reason(self, result):
        self.assertEqual("block", result["decision"])
        return result["reason"]

    def test_code_change_without_docs_is_blocked_and_names_the_docs(self):
        self.start_turn()
        self.write(SERVICE, "v2\n")
        reason = self.reason(self.stop())
        self.assertIn("README.md", reason)
        self.assertIn(".claude/CLAUDE.md", reason)
        self.assertIn("FooService.java", reason)
        self.assertNotIn("banking-openapi.yaml", reason)

    def test_code_change_with_readme_and_claude_docs_passes(self):
        self.start_turn()
        self.write(SERVICE, "v2\n")
        self.write("README.md", "v2\n")
        self.write(".claude/rules/banking/design.md", "v2\n")
        self.assertIsNone(self.stop())

    def test_controller_change_also_needs_the_openapi_spec(self):
        self.start_turn()
        self.write(CONTROLLER, "v2\n")
        self.write("README.md", "v2\n")
        self.write(".claude/CLAUDE.md", "v2\n")
        self.assertIn("banking-openapi.yaml", self.reason(self.stop()))
        self.write(OPENAPI, "v2\n")
        self.assertIsNone(self.stop())

    def test_entity_change_also_needs_db_docs(self):
        self.start_turn()
        self.write(ENTITY, "v2\n")
        self.write("README.md", "v2\n")
        self.write(".claude/CLAUDE.md", "v2\n")
        self.assertIn("db/", self.reason(self.stop()))
        self.write("db/ddl/01_create_tables.sql", "v2\n")
        self.assertIsNone(self.stop())

    def test_a_new_entity_needs_ddl_drop_migration_dml_data_and_readme(self):
        self.start_turn()
        self.write(NEW_ENTITY, "class BarEntity {}\n")
        self.write("README.md", "v2\n")
        self.write(".claude/CLAUDE.md", "v2\n")
        self.write("db/ddl/01_create_tables.sql", "v2\n")
        reason = self.stop()["reason"]
        for expected in ("02_drop_tables.sql", "migration", "db/dml/", "db/data/", "db/README.md"):
            self.assertIn(expected, reason)
        self.assertNotIn("01_create_tables.sql (the new table", reason)
        self.assertIn("BarEntity.java", reason)

    def test_a_new_entity_with_the_full_db_set_passes(self):
        self.start_turn()
        self.write(NEW_ENTITY, "class BarEntity {}\n")
        self.write("README.md", "v2\n")
        self.write(".claude/CLAUDE.md", "v2\n")
        for rel in ("db/ddl/01_create_tables.sql", "db/ddl/02_drop_tables.sql", "db/ddl/08_bar_migration.sql",
                    "db/dml/05_bar.sql", "db/data/10_backup_bar.sql", "db/README.md"):
            self.write(rel, "v2\n")
        self.assertIsNone(self.stop())

    def test_editing_an_existing_entity_does_not_demand_the_full_new_table_set(self):
        self.start_turn()
        self.write(ENTITY, "v2\n")
        self.write("README.md", "v2\n")
        self.write(".claude/CLAUDE.md", "v2\n")
        self.write("db/ddl/01_create_tables.sql", "v2\n")
        self.assertIsNone(self.stop())

    def test_config_and_pom_changes_need_readme_and_claude_docs(self):
        self.start_turn()
        self.write("src/main/resources/application.yml", "v2\n")
        self.write("pom.xml", "v2\n")
        reason = self.reason(self.stop())
        self.assertIn("README.md", reason)
        self.assertIn(".claude/CLAUDE.md", reason)

    def test_hook_or_settings_changes_need_claude_docs_but_not_readme(self):
        self.start_turn()
        self.write(".claude/hooks/x.py", "v2\n")
        reason = self.reason(self.stop())
        self.assertIn(".claude/CLAUDE.md", reason)
        self.assertNotIn("update README.md", reason)

    def test_test_only_and_docs_only_changes_pass(self):
        self.start_turn()
        self.write(TEST, "v2\n")
        self.write("README.md", "v2\n")
        self.assertIsNone(self.stop())

    def test_untracked_new_files_and_deleted_files_count(self):
        self.start_turn()
        self.write("src/main/java/org/brite/banking/service/NewService.java", "new\n")
        self.assertIn("NewService.java", self.reason(self.stop()))
        self.start_turn()
        (self.repo / SERVICE).unlink()
        self.assertIn("FooService.java", self.reason(self.stop()))

    def test_files_dirty_before_the_turn_only_count_if_they_change_again(self):
        self.write(SERVICE, "dirty before the turn\n")
        self.start_turn()
        self.assertIsNone(self.stop())
        self.write(SERVICE, "changed again\n")
        self.assertIn("FooService.java", self.reason(self.stop()))

    def test_committing_during_the_turn_is_not_a_change(self):
        self.write(SERVICE, "dirty\n")
        self.start_turn()
        self.git("-c", "user.email=t@t", "-c", "user.name=t", "commit", "-qam", "c")
        self.assertIsNone(self.stop())

    def test_the_escape_line_in_the_final_message_allows_the_stop(self):
        self.start_turn()
        self.write(SERVICE, "v2\n")
        transcript = self.repo / "t.jsonl"
        transcript.write_text(json.dumps({"type": "assistant", "message": {"role": "assistant", "content": [
            {"type": "text", "text": "Renamed a private helper.\n\nDocs: no update needed (internal rename, no behaviour change)"}]}}) + "\n")
        self.assertIsNone(self.stop(transcript_path=str(transcript)))

    def test_it_never_blocks_more_than_twice_in_one_turn(self):
        self.start_turn()
        self.write(SERVICE, "v2\n")
        self.reason(self.stop())
        self.reason(self.stop(stop_hook_active=True))
        self.assertIsNone(self.stop(stop_hook_active=True))

    def test_a_new_turn_resets_the_counter_and_baseline(self):
        self.start_turn()
        self.write(SERVICE, "v2\n")
        self.reason(self.stop())
        self.start_turn()          # same dirty file, but nothing changed in this new turn
        self.assertIsNone(self.stop())

    def test_without_a_baseline_it_does_nothing(self):
        self.write(SERVICE, "v2\n")
        self.assertIsNone(self.stop())

    def test_local_only_files_are_ignored(self):
        self.start_turn()
        self.write("CLAUDE.local.md", "private\n")
        self.write(".claude/settings.local.json", "{}\n")
        self.assertIsNone(self.stop())


if __name__ == "__main__":
    unittest.main()

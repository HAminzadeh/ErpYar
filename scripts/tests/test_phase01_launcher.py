"""Phase 01 dependency-free integration checks (Java 21 only; no Maven/Docker needed)."""
from __future__ import annotations

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA_PACKAGE = ROOT / "tools/erp-launcher/src/main/java"
MAIN = "ir.erp.launcher.ErpYarLauncher"


@unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "Java compiler/runtime missing")
class Phase01LauncherContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="erpyar-phase01-")
        cls.classes = Path(cls.temp.name) / "classes"
        cls.classes.mkdir()
        sources = sorted(str(p) for p in JAVA_PACKAGE.rglob("*.java"))
        result = subprocess.run(["javac", "--release", "21", "-encoding", "UTF-8",
                                 "-d", str(cls.classes), *sources],
                                capture_output=True, text=True, timeout=90)
        if result.returncode:
            raise AssertionError("Java 21 compile failure:\n" + result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def setUp(self):
        self.fixture = tempfile.TemporaryDirectory(prefix="erp workspace with spaces ")
        self.root = Path(self.fixture.name)
        (self.root / "pom.xml").write_text("<project/>", encoding="utf-8")
        (self.root / "compose.yaml").write_text("services: {}", encoding="utf-8")
        (self.root / "services/identity").mkdir(parents=True)
        (self.root / "tools/erp-launcher/src/main/java").mkdir(parents=True)
        self.nested = self.root / "tools/erp-launcher/src/main/java"

    def tearDown(self):
        self.fixture.cleanup()

    def run_app(self, *args):
        return subprocess.run(["java", "-cp", str(self.classes), MAIN, "--repo", str(self.nested), *args],
                              capture_output=True, text=True, timeout=60, cwd=self.root)

    def test_nested_repo_and_env_idempotent(self):
        a = self.run_app("--env-only", "--json")
        self.assertEqual(a.returncode, 0, a.stderr)
        data = json.loads(a.stdout)
        self.assertEqual(data["outcome"], "ENVIRONMENT_READY")
        self.assertIn(".env", [p.name for p in self.root.iterdir()])
        initial = (self.root / ".env").read_bytes()
        b = self.run_app("--env-only", "--json")
        self.assertEqual(b.returncode, 0, b.stderr)
        self.assertEqual((self.root / ".env").read_bytes(), initial)

    def test_doctor_no_files_added_and_json_valid(self):
        before = sorted(str(p.relative_to(self.root)) for p in self.root.rglob("*"))
        result = self.run_app("--doctor", "--json")
        self.assertIn(result.returncode, (0, 2))
        data = json.loads(result.stdout)
        self.assertEqual(data["mode"], "DOCTOR")
        self.assertTrue(any(x["id"] == "java" for x in data["checks"]))
        self.assertTrue(any(x["id"] == "docker-engine" for x in data["checks"]))
        after = sorted(str(p.relative_to(self.root)) for p in self.root.rglob("*"))
        self.assertEqual(before, after)

    def test_plan_never_creates_env_or_starts_containers(self):
        result = self.run_app("--plan", "--json")
        self.assertIn(result.returncode, (0, 2))
        data = json.loads(result.stdout)
        self.assertEqual(data["mode"], "PLAN")
        self.assertTrue(any(x["stage"] == "SERVICE_PLAN" and x["result"] == "DEFERRED"
                            for x in data["checks"]))
        self.assertFalse((self.root / ".env").exists())

    def test_bad_repo_path_does_not_create_folder(self):
        bogus = self.root / "not-found"
        result = subprocess.run(["java", "-cp", str(self.classes), MAIN,
                                 "--repo", str(bogus), "--doctor"],
                                capture_output=True, text=True, timeout=15)
        self.assertEqual(result.returncode, 2)
        self.assertFalse(bogus.exists())

    def test_source_disables_old_all_java_compose(self):
        source = (JAVA_PACKAGE / "ir/erp/launcher/ComposeOrchestrator.java").read_text()
        self.assertNotIn('"up", "-d", "--build"', source)
        self.assertIn('Legacy Docker orchestration of Java services was disabled', source)


if __name__ == "__main__":
    unittest.main()

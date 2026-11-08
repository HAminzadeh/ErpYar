"""Offline, hermetic tests for the structural baseline checker."""
import sys
import tempfile
import unittest
from pathlib import Path
from xml.sax.saxutils import escape

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_baseline import BUSINESS, DATABASES, MODULES, inspect


class BaselineTests(unittest.TestCase):
    def make_repo(self, root: Path):
        pom = "<project xmlns='http://maven.apache.org/POM/4.0.0'><parent><version>4.0.8</version></parent>" \
              "<properties><java.version>21</java.version><spring-cloud.version>2025.1.3</spring-cloud.version></properties>" \
              "<modules>" + "".join(f"<module>{escape(m)}</module>" for m in MODULES) + "</modules></project>"
        (root / "pom.xml").write_text(pom, encoding="utf-8")
        for m in MODULES:
            (root / m).mkdir(parents=True, exist_ok=True)
            (root / m / "pom.xml").write_text("<project/>", encoding="utf-8")
        db = root / "infra/postgres/01-init-databases.sql"
        db.parent.mkdir(parents=True)
        db.write_text("\n".join(DATABASES.values()))
        compose = root / "compose.yaml"
        compose.write_text("\n".join(f"DB_NAME: {v}" for v in DATABASES.values()) + "\n${JWT_SECRET:?} ${DB_PASSWORD:?}")
        gateway = root / "services/gateway/src/main/resources/application.yml"
        gateway.parent.mkdir(parents=True)
        gateway.write_text("include: health\n" + "\n".join(f"http://{s}:8080" for s in BUSINESS))
        for s in BUSINESS:
            p = root / f"services/{s}/src/main/resources/db/migration/V1__init.sql"
            p.parent.mkdir(parents=True)
            p.write_text("-- fixture only")
        for name in ("scripts/init_env.py", "scripts/smoke_test.py", "Dockerfile"):
            p = root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("# fixture")

    def test_expected_structure_passes_offline(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.make_repo(root)
            r = inspect(root)
            self.assertEqual(r["errors"], [])
            self.assertEqual(r["checks"]["structural"], "PASS")
            self.assertEqual(r["checks"]["mavenVerify"], "NOT_RUN")

    def test_missing_service_migration_is_blocker(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.make_repo(root)
            (root / "services/workflow/src/main/resources/db/migration/V1__init.sql").unlink()
            r = inspect(root)
            self.assertTrue(any("workflow" in item for item in r["errors"]))

    def test_missing_company_db_mapping_is_blocker(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.make_repo(root)
            compose = root / "compose.yaml"
            compose.write_text(compose.read_text().replace("DB_NAME: audit_db", "DB_NAME: incorrect_db"))
            r = inspect(root)
            self.assertTrue(any("audit" in item for item in r["errors"]))


if __name__ == "__main__":
    unittest.main()

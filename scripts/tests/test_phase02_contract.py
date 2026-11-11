"""Static Phase 02 contract tests: deliberately run without Docker or network."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'tools/erp-launcher/src/main/java/ir/erp/launcher'

class Phase02Contract(unittest.TestCase):
    def test_compose_only_postgres_and_reuses_phase1_volume(self):
        text=(ROOT/'infra/compose.infrastructure.yml').read_text(encoding='utf-8')
        self.assertIn('name: erp-phase1',text)
        self.assertIn('  postgres:',text)
        self.assertIn('  postgres_data:',text)
        self.assertIn('pull_policy: never',text)
        self.assertIn('127.0.0.1:${ERP_DB_HOST_PORT:-15432}:5432',text)
        self.assertNotIn('down -v',text)

    def test_cache_path_is_ignored_by_git(self):
        ignore=(ROOT/'.gitignore').read_text(encoding='utf-8')
        self.assertIn('/.local/',ignore)

    def test_no_java_services_built_in_new_compose(self):
        text=(ROOT/'infra/compose.infrastructure.yml').read_text(encoding='utf-8')
        for service in ('gateway:','identity:','organization:','master-data:','workflow:','notification:','audit:'):
            self.assertNotIn('\n  '+service,text)
        self.assertNotIn('build:',text)

    def test_cli_and_code_contracts(self):
        cli=(JAVA/'ErpYarLauncher.java').read_text(encoding='utf-8')
        self.assertIn('--offline',cli)
        for flag in ('--cache-export','--cache-import','--infra-only'):
            self.assertIn(flag,cli)
        cache=(JAVA/'DockerImageCacheManager.java').read_text(encoding='utf-8')
        self.assertIn('ManifestValidator.validatedTar',cache)
        self.assertIn('image","load',cache)
        self.assertIn('image","save',cache)
        postgres=(JAVA/'PostgresInfrastructureManager.java').read_text(encoding='utf-8')
        self.assertIn('new DatabaseConnectionProbe',postgres)
        self.assertNotIn('down -v',postgres)

if __name__ == '__main__': unittest.main()

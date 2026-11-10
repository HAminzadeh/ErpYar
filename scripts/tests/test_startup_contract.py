"""Offline contract tests: source-only, no Docker/database/API connection required."""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]


class StartupContractTest(unittest.TestCase):
    def test_root_maven_includes_launcher(self):
        doc = ET.parse(ROOT / 'pom.xml')
        modules = [e.text for e in doc.findall('.//{*}module')]
        self.assertIn('tools/erp-launcher', modules)
        self.assertEqual(modules.count('tools/erp-launcher'), 1)

    def test_compose_exposes_postgres_to_loopback_only(self):
        text = (ROOT / 'compose.yaml').read_text(encoding='utf-8')
        self.assertIn('127.0.0.1:${ERP_DB_HOST_PORT:-15432}:5432', text)
        self.assertNotIn('docker compose down -v', text)

    def test_all_business_services_load_env_and_ide_profile(self):
        for name in ('identity', 'organization', 'master-data', 'workflow', 'notification', 'audit'):
            with self.subTest(service=name):
                directory = ROOT / 'services' / name / 'src/main/resources'
                yml = (directory / 'application.yml').read_text(encoding='utf-8')
                ide = (directory / 'application-ide.yml').read_text(encoding='utf-8')
                self.assertIn('optional:file:./.env[.properties]', yml)
                self.assertIn('127.0.0.1:${ERP_DB_HOST_PORT:15432}', ide)

    def test_idea_run_configuration_uses_launcher(self):
        path = ROOT / '.run/ErpYar - Start All Services.run.xml'
        doc = ET.parse(path)
        self.assertTrue(any(node.get('value') == 'ir.erp.launcher.ErpYarLauncher'
                            for node in doc.findall('.//option')))
        self.assertTrue(any(node.get('value') == '$PROJECT_DIR$'
                            for node in doc.findall('.//option')))


if __name__ == '__main__':
    unittest.main()

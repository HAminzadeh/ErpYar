"""Phase 02.1: a single shared IntelliJ Run button, independent of Docker/Maven."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]

class UnifiedEntryPointTest(unittest.TestCase):
    def test_only_one_shared_run_configuration(self):
        configs = sorted((ROOT / '.run').glob('*.run.xml'))
        self.assertEqual([p.name for p in configs], ['ErpYar - Run.run.xml'])
        document = ET.parse(configs[0])
        config = document.find('.//configuration')
        self.assertEqual(config.attrib['name'], 'ErpYar - Run')
        options = {e.attrib.get('name'): e.attrib.get('value') for e in config.findall('./option')}
        self.assertEqual(options['MAIN_CLASS_NAME'], 'ir.erp.launcher.ErpYarLauncher')
        self.assertEqual(options['WORKING_DIRECTORY'], '$PROJECT_DIR$')
        self.assertEqual(options['PROGRAM_PARAMETERS'], '--start')
        self.assertEqual(config.find('module').attrib['name'], 'erp-launcher')

    def test_legacy_compose_java_is_not_startup_path(self):
        orchestrator = (ROOT/'tools/erp-launcher/src/main/java/ir/erp/launcher/StartupOrchestrator.java').read_text()
        self.assertIn('new InfrastructureManager(', orchestrator)
        self.assertNotIn('new ComposeOrchestrator(', orchestrator)
        source = (ROOT/'tools/erp-launcher/src/main/java/ir/erp/launcher/ErpYarLauncher.java').read_text()
        for flag in ('--doctor', '--cache-export', '--cache-import', '--offline', '--infra-only', '--start'):
            self.assertIn(flag, source)

if __name__ == '__main__':
    unittest.main()

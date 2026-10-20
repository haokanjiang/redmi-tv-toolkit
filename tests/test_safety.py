import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).parents[1]
A = "{http://schemas.android.com/apk/res/android}"

class ManifestSecurityTests(unittest.TestCase):
    def setUp(self):
        self.root = ET.parse(ROOT / "app/AndroidManifest.xml").getroot()
        self.app = self.root.find("application")

    def test_control_permission_and_no_unguarded_mutation_entry(self):
        for name, tag in [(".ToolkitControlReceiver", "receiver"), (".ControlActivity", "activity")]:
            node = next(n for n in self.app.findall(tag) if n.get(A+"name") == name)
            self.assertEqual("true", node.get(A+"exported"))
            self.assertEqual("android.permission.DUMP", node.get(A+"permission"))
        boot = next(n for n in self.app.findall("receiver") if n.get(A+"name") == ".BootReceiver")
        self.assertEqual("false", boot.get(A+"exported"))
        for node in self.app.findall("service"):
            if node.get(A+"exported") == "true":
                self.assertEqual("android.permission.BIND_ACCESSIBILITY_SERVICE", node.get(A+"permission"))

    def test_boot_and_jobs_manifest_requirements(self):
        permissions = {n.get(A+"name") for n in self.root.findall("uses-permission")}
        self.assertIn("android.permission.RECEIVE_BOOT_COMPLETED", permissions)
        self.assertIn("android.permission.WRITE_SECURE_SETTINGS", permissions)
        job = next(n for n in self.app.findall("service") if n.get(A+"name") == ".RecoveryJobService")
        self.assertEqual("android.permission.BIND_JOB_SERVICE", job.get(A+"permission"))
        self.assertEqual("true", self.app.get(A+"directBootAware"))
        self.assertEqual("34", self.root.find("uses-sdk").get(A+"targetSdkVersion"))

    def test_no_home_handler_in_helper(self):
        for node in self.app.findall("activity"):
            for category in node.findall("intent-filter/category"):
                self.assertNotEqual("android.intent.category.HOME", category.get(A+"name"))

if __name__ == "__main__":
    unittest.main()

import unittest
from pathlib import Path
ROOT=Path(__file__).parents[1]
class SafetyTests(unittest.TestCase):
    def test_mftr0_profile_and_service(self):
        p=(ROOT/'app/src/local/blueplanet/boothelper/DeviceProfile.java').read_text()
        self.assertIn('freeguy',p); self.assertIn('MiTV-MFTR0',p)
        s=(ROOT/'app/src/local/blueplanet/boothelper/HomeOverrideService.java').read_text()
        self.assertIn('TYPE_WINDOWS_CHANGED',s); self.assertIn('MAX_ATTEMPTS', (ROOT/'app/src/local/blueplanet/boothelper/HomeOverrideRetry.java').read_text())
    def test_no_unsafe_home_commands(self):
        for p in (ROOT/'scripts').glob('*.ps1'):
            text=p.read_text().lower()
            self.assertNotIn('pm disable-user',text,p.name)
            self.assertNotIn('set-home-activity',text,p.name)
    def test_launcher_filter_is_home_first(self):
        p=(ROOT/'scripts/common.ps1').read_text()
        self.assertIn('query-activities',p); self.assertIn('IsHome',p)
if __name__=='__main__': unittest.main()

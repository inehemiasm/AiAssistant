import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("readiness", ROOT / "scripts/check_release_readiness.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReleaseReadinessTest(unittest.TestCase):
    def setUp(self):
        self.ledger = json.loads((ROOT / "docs/release-readiness.json").read_text(encoding="utf-8"))

    def test_unresolved_external_gates_are_not_release_ready(self):
        result = module.evaluate(self.ledger, "targetSdk = 37")
        self.assertFalse(result["readyForPlay"])
        self.assertIn("protected_reporting", result["blockedGates"])

    def test_omitting_a_gate_cannot_bypass_the_check(self):
        ledger = copy.deepcopy(self.ledger)
        ledger["gates"].pop()
        self.assertTrue(module.evaluate(ledger, "targetSdk = 37")["errors"])

    def test_strict_command_fails_closed_for_current_blockers(self):
        result = subprocess.run([sys.executable, str(ROOT / "scripts/check_release_readiness.py"), "--strict"], capture_output=True)
        self.assertNotEqual(0, result.returncode)


if __name__ == "__main__":
    unittest.main()

"""Validate the release evidence ledger; --strict refuses unresolved Play release gates.

This checks declared evidence completeness, not legal compliance or Play approval.
It never publishes artifacts, changes the backend, or reads signing secrets.
"""
import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = {"publisher_identity", "target_audience", "play_console", "privacy_publication",
            "protected_reporting", "report_operations", "data_safety", "license_review",
            "safety_evaluation", "release_signing", "release_artifact", "device_matrix", "target_api"}


def evaluate(ledger, gradle_text):
    errors = []
    if ledger.get("schemaVersion") != 1:
        errors.append("Unsupported ledger version")
    gates = ledger.get("gates", [])
    ids = [gate.get("id") for gate in gates]
    if set(ids) != REQUIRED or len(ids) != len(set(ids)):
        errors.append("Every required gate must be present exactly once")
    for gate in gates:
        if gate.get("status") not in {"verified", "blocked"} or not str(gate.get("evidence", "")).strip():
            errors.append("Gate needs an explicit status and evidence: " + str(gate.get("id")))
    target = re.search(r"targetSdk\s*=\s*(\d+)", gradle_text)
    if not target or int(target[1]) < 36:
        errors.append("Target API source check failed; verify the current Play requirement")
    blocked = [gate["id"] for gate in gates if gate.get("status") != "verified"]
    return {"readyForPlay": not errors and not blocked, "errors": errors,
            "blockedGates": blocked, "note": "Declaration check only; not legal clearance or Play approval."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    ledger = json.loads((ROOT / "docs/release-readiness.json").read_text(encoding="utf-8"))
    result = evaluate(ledger, (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8"))
    text = json.dumps(result, indent=2) + "\n"
    if args.output:
        args.output.write_text(text, encoding="utf-8")
    print(text, end="")
    return 1 if result["errors"] or (args.strict and not result["readyForPlay"]) else 0


if __name__ == "__main__":
    raise SystemExit(main())

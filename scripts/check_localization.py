"""Check locale coverage, duplicate keys, format arguments, and literal UI copy.

Uses only the Python standard library; suitable for CI without an Android device.
Protocol payloads, model prompts, logs, user/external content, and preview fixtures
are not UI translations. Their identifiers must not change with the locale.
"""
from pathlib import Path
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LANGUAGES = ("es", "fr", "de")
FORMAT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])")


def arguments(text):
    result = {}
    for index, match in enumerate(FORMAT.finditer(text.replace("%%", "")), 1):
        result[int(match[1] or index)] = match[2].lower()
    return result


def resources(path, errors):
    result = {}
    if not path.exists():
        errors.append(f"Missing resource file: {path.relative_to(ROOT)}")
        return result
    for item in ET.parse(path).getroot():
        if "name" not in item.attrib:
            continue
        key = item.tag, item.attrib["name"]
        if key in result:
            errors.append(f"Duplicate {key}: {path.relative_to(ROOT)}")
        result[key] = item
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path)
    options = parser.parse_args()
    errors = []
    summary = {}
    for module in ("app", "ui-designsystem"):
        directory = ROOT / module / "src/main/res"
        base = resources(directory / "values/strings.xml", errors)
        required = {key: item for key, item in base.items() if item.attrib.get("translatable") != "false"}
        summary[module] = {"translatable_entries": len(required), "locales": {}}
        for language in LANGUAGES:
            translated = resources(directory / f"values-{language}/strings.xml", errors)
            missing = sorted(set(required) - set(translated))
            extra = sorted(set(translated) - set(base))
            for key in missing:
                errors.append(f"{module}/{language}: missing {key}")
            for key in extra:
                errors.append(f"{module}/{language}: unknown {key}")
            for key in set(required) & set(translated):
                original, localized = required[key], translated[key]
                if key[0] == "plurals":
                    expected = {item.attrib["quantity"]: item.text or "" for item in original}
                    actual = {item.attrib["quantity"]: item.text or "" for item in localized}
                    if "other" not in actual:
                        errors.append(f"{module}/{language}/{key[1]}: missing plural 'other'")
                    pairs = [(expected.get(quantity, expected["other"]), text) for quantity, text in actual.items()]
                else:
                    pairs = [("".join(original.itertext()), "".join(localized.itertext()))]
                for source, target in pairs:
                    if not target.strip():
                        errors.append(f"{module}/{language}/{key[1]}: empty translation")
                    if arguments(source) != arguments(target):
                        errors.append(f"{module}/{language}/{key[1]}: format arguments differ")
            summary[module]["locales"][language] = {"missing": len(missing), "extra": len(extra)}

    # These are stable technical symbols or branding, not English UI sentences.
    symbols = {"CHEVERE AI", "dB SPL", "Δ uT"}
    literal = re.compile(r'(?:\bText\s*\(\s*|\b(?:text|contentDescription)\s*=\s*)"((?:\\.|[^"\\])*)"')
    ui_roots = [ROOT / "app/src/main/java/com/neo/chevere/ui", ROOT / "ui-designsystem/src/main/java"]
    for directory in ui_roots:
        for path in directory.rglob("*.kt"):
            source = path.read_text(encoding="utf-8")
            preview = re.search(r"@(?:androidx\.compose\.ui\.tooling\.preview\.)?Preview\b", source)
            if preview:
                source = source[:preview.start()]
            for match in literal.finditer(source):
                text = match[1]
                if "$" in text or text in symbols or not re.search(r"[A-Za-z]", text):
                    continue
                line = source.count("\n", 0, match.start()) + 1
                errors.append(f"{path.relative_to(ROOT)}:{line}: literal UI text {text!r}")

    report = {"languages": ["en", *LANGUAGES], "modules": summary, "errors": errors}
    if options.report:
        options.report.parent.mkdir(parents=True, exist_ok=True)
        options.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return bool(errors)


if __name__ == "__main__":
    sys.exit(main())

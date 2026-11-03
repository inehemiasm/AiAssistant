"""Generate Play build identity separately from the reviewed semantic version."""
import argparse
import json
from pathlib import Path
import re
import time

ROOT = Path(__file__).resolve().parents[1]
# Seconds since 2026-01-01 UTC, comfortably below Play's integer ceiling for decades.
EPOCH = 1767225600
MAX_CODE = 2_100_000_000


def version_name(path=ROOT / "version.properties"):
    parts = dict(line.strip().split("=", 1) for line in path.read_text().splitlines()
                 if line.strip() and not line.lstrip().startswith("#"))
    values = [parts[key] for key in ("major", "minor", "patch")]
    if not all(re.fullmatch(r"0|[1-9][0-9]*", value) for value in values):
        raise ValueError("Version components must be canonical non-negative integers")
    return ".".join(values)


def version_code(now=None):
    code = int(time.time() if now is None else now) - EPOCH
    if not 1 <= code <= MAX_CODE:
        raise ValueError("Generated version code is outside the Play range")
    return code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--github-output", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = {"versionName": version_name(), "versionCode": version_code()}
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as stream:
            stream.write(f"version_name={result['versionName']}\nversion_code={result['versionCode']}\n")
    text = json.dumps(result, indent=2) + "\n"
    if args.output:
        args.output.write_text(text, encoding="utf-8")
    print(text, end="")


if __name__ == "__main__":
    main()

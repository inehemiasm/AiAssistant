"""Run the localization UI flow using Android per-app locales, restoring device settings.

Requires an unlocked device, ADB, and Maestro. Does not change system languages,
clear app data, download models, send chat messages, or submit reports.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev.neo.chevereai"
KEYS = {
    "LIBRARY": "menu_library", "MODELS_LIBRARY": "ui_models_library",
    "GUIDANCE_TITLE": "model_guidance_title", "LEARN_MORE": "model_guidance_learn_more",
    "DONE": "model_guidance_done", "TASKS": "tasks_label", "ADD_TASK": "ui_add_task",
    "TASK_DIALOG": "add_task_title", "CANCEL": "cancel_button",
    "CHAT": "chat_label",
    "SETTINGS": "ui_system_settings", "PRIVACY": "safety_privacy", "LOCAL_FIRST": "local_first_ai",
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--languages", nargs="+", default=["en", "es", "fr", "de"], choices=["en", "es", "fr", "de"])
    parser.add_argument("--maestro", default=r"C:\maestro\bin\maestro.bat" if os.name == "nt" else "maestro")
    args = parser.parse_args()
    adb = ["adb", "-s", args.serial]

    def shell(*command):
        return subprocess.check_output([*adb, "shell", *command], text=True, encoding="utf-8").strip()

    old_locale = re.search(r"are \[(.*?)\]", shell("cmd", "locale", "get-app-locales", PACKAGE))[1]
    old_awake = shell("settings", "get", "global", "stay_on_while_plugged_in")
    if not re.fullmatch(r"\d+", old_awake):
        raise RuntimeError("Cannot safely save the stay-awake setting")
    output = ROOT / "notes/localization-device"
    output.mkdir(parents=True, exist_ok=True)
    (output / "original-settings.json").write_text(json.dumps({"app_locales": old_locale, "stay_awake": old_awake, "serial": args.serial}), encoding="utf-8")
    results = {}
    try:
        shell("svc", "power", "stayon", "true")
        shell("input", "keyevent", "KEYCODE_WAKEUP")
        shell("wm", "dismiss-keyguard")
        for language in args.languages:
            print(f"Testing {language}", flush=True)
            shell("cmd", "locale", "set-app-locales", PACKAGE, "--locales", language)
            shell("am", "start", "-n", PACKAGE + "/com.neo.chevere.MainActivity")
            resource_path = ROOT / "app/src/main/res" / ("values" if language == "en" else "values-" + language) / "strings.xml"
            strings = {item.attrib["name"]: (item.text or "").replace("\\'", "'").replace('\\"', '"') for item in ET.parse(resource_path).getroot() if item.tag == "string"}
            command = [args.maestro, "test", "--udid", args.serial, "--test-output-dir", str(output / language), "-e", "LANGUAGE=" + language]
            for variable, resource in KEYS.items():
                command.extend(["-e", variable + "=" + re.escape(strings[resource])])
            command.append(str(ROOT / ".maestro/localization_validation.yaml"))
            with (output / (language + ".log")).open("w", encoding="utf-8") as log:
                result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            results[language] = "passed" if result.returncode == 0 else "failed"
            print(f"{language}: {results[language]}", flush=True)
            if result.returncode:
                break
    finally:
        restore = ["cmd", "locale", "set-app-locales", PACKAGE]
        if old_locale:
            restore.extend(["--locales", old_locale])
        shell(*restore)
        shell("settings", "put", "global", "stay_on_while_plugged_in", old_awake)
        (output / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
        print("Restored original app language and sleep setting", flush=True)
    return 0 if len(results) == len(args.languages) and all(value == "passed" for value in results.values()) else 1


if __name__ == "__main__":
    sys.exit(main())

# App localization audit

Chevere declares English, Spanish, French, and German in
`app/src/main/res/xml/locales_config.xml`. Android 13 and newer can select a
language for this app independently of the system language. Earlier versions
use the device configuration and fall back to English for unsupported languages.

The audit covers app-authored navigation, dialogs, accessibility descriptions,
model guidance and catalog descriptions, tasks and invoices, history and counts,
sensor screens, diagnostics, permission and privacy disclosures, voice errors,
document import, download notifications and failures, runtime initialization,
deterministic capability answers, and action/MCP confirmations. Slash-command
labels live in presentation resources; their command tokens stay unchanged.

The audit also corrects outdated claims about zero latency, image generation,
calendar data, report sharing, and guaranteed removal of personal identifiers.
French accents and German umlauts missing from older translations are restored.

## Translation boundaries

- App-owned display text uses Android string or plural resources. Resource IDs
  and enum-to-label mappings stay in Android data/presentation adapters.
- Model names, license identifiers, URLs, filenames, units, tool names, argument
  schemas, JSON keys, slash commands, logs, and model instructions are stable
  technical data. Do not translate them as UI labels or alter their semantics.
- User-entered tasks, invoices, documents, stored chats, generated model output,
  server-provided content, and raw tool/SDK diagnostics retain their original
  text. Localization does not rewrite conversations or promise that a local
  model will always answer in a particular language.
- Platform permission dialogs, speech services, calendar applications, and the
  open-source notices viewer provide their own localization.
- Published policy/terms documents are separate release artifacts. This change
  does not publish policy drafts or change reporting/backend access controls.

## Verification

Run `python scripts/check_localization.py --report docs/localization-audit.json`.
The report records coverage for the app and design system. The check rejects
missing or duplicate resources, mismatched format arguments, and literal UI copy
in common display APIs; it also runs in CI. It is a regression guard, not a
substitute for reviewing every new text-producing path.

`LocalizationResourcesTest` exercises actual Android language selection,
pluralization, notification formatting, localized confirmations, and unchanged
tool arguments. The app debug unit suite, APK build, and lint must pass.

On an unlocked Android 13+ device with ADB and Maestro installed, run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$env:MAESTRO_CLI_NO_ANALYTICS='1'
$env:MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED='true'
python scripts/validate_localization_device.py --serial <device-serial>
```

This runs `.maestro/localization_validation.yaml` for all four languages through
Models, task and invoice dialogs, and privacy settings. Screenshots and results
are saved under ignored `notes/localization-device/`. Original per-app language
and plugged-in sleep settings are restored in `finally`; the original values
are also saved there for recovery after an interrupted process. No app data is
cleared, no models are downloaded, and no reports or chat requests are submitted.

Review screenshots for wrapping, clipping, accessibility, and grammatical
quality. Resource coverage alone does not establish translation quality.

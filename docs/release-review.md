# Release review — November 1, 2026

This is an evidence ledger, not a compliance certificate or legal opinion. Do not publish while the gates below remain unresolved.

## Current reliability/release review

The current declaration ledger is [release-readiness.json](release-readiness.json).
Historical validation below is not current release clearance. The owner plans to
publish personally and explicitly kept the audience undecided. Exact verified
legal name, Play verification and report operations still require confirmation.
The selected contact address remains `nehemias.miranda.work@gmail.com`.

- Read-only CLI verification confirmed Standard `(default)` / `us-central1` in
  `aiassistant-88f75`. Report intake remains closed; no backend writes, deletion
  schedule, billing change, policy publication or Play upload was performed.
- Current source targets API 37; the referenced phone/tablet submission requirement
  is API 36+. Verify Console eligibility at submission time.
- Refreshed release dependency inventory: 327 components, 22 without local POM
  license metadata. Every applicable LICENSE/NOTICE and model/conversion/asset
  right still requires review; the inventory is not clearance.
- Signed/minified release artifacts and alignment evidence are recorded alongside
  current tests. The public local signing certificate fingerprint is
  `c2000cd5ecbc1a4668f58bf737db0ca6719fb2d9e7e79b54fa9b628cb38c312e`;
  comparison with the Play upload certificate remains unverified.
- Run `python scripts/check_release_readiness.py --strict` before an intended Play
  release. It intentionally fails for unresolved gates. Default evidence CI only
  validates declaration completeness, not safety/legal/Console approval.

See [chat reliability](chat-reliability.md) for context budgeting, measured usage,
source evidence and agent status. Full safety evaluation, protected report intake,
reviewer/retention/deletion operations, verified public policies/Data Safety,
rights review, pre-launch reports and the remaining device matrix stay open.

## Implemented controls

- Production signing no longer falls back to the debug key. `validateSigningRelease` rejects missing signing configuration. Do not commit signing secrets.
- Privacy source and onboarding disclose optional network tools, MCP arguments, reports, and external destinations. Conversation deletion is distinguished from clearing all local app data.
- Remote MCP confirmation explains the data transfer and third-party privacy handling.
- Nonfatal telemetry no longer sends raw throwable messages/causes. Fatal crash capture and all other SDK data still need review.
- AI safety instructions cover direct chat, vision, agent runs, and suggestion prediction. Instructions are not a sufficient output safety system.
- Report timeouts return failure instead of success; ordinary coroutine cancellation propagates.
- `scripts/check_native_alignment.py ARTIFACT` validates 64-bit ELF load-segment alignment. ZIP alignment and a real 16 KB device/emulator test are separate requirements.
- The obsolete MediaPipe GenAI dependency was removed after its native library failed alignment checks. Verify the rebuilt artifact before claiming compatibility.
- Bundled Gemma 4 catalog entries use Apache-2.0; older Gemma entries retain their separate terms. Conversion repository notices and dynamically discovered models still require individual review.

## Additional findings

- Initial territory confirmed by the owner: United States. Publisher identity and working support email are not finalized.
- Existing release signing is configured locally; preserve the key and compare its certificate with Play Console. Do not infer Play registration from a local signing success.
- The old baseline profile contained invalid method entries without signatures. Those entries were removed; regenerate comprehensive profiles later using the baseline-profile module.
- Release lint identified missing vibration permission, location SecurityException handling, and a non-observable Compose locale read; those were corrected. Incomplete translations remain visible warnings, with English fallback for corrected disclosures.
- Android speech recognition requests offline mode, which is advisory. Disclosures now identify speech-provider behavior as potentially network-backed.
- Google OSS Licenses is integrated into Settings through MVI for packaged dependency notices. Models, conversions, branding and assets still need separate review.

## Production gates requiring evidence

| Gate | Required evidence |
|---|---|
| Publisher and territories | Legal publisher name, verified support/privacy address, target countries and intended age group. No trademark/name clearance has been performed. |
| Privacy publication | Review and deploy updated policy; verify the public URL matches the shipped behavior. Current source changes are not automatically deployed. |
| Report retention/deletion | Choose retention period, configure actual server deletion, verify deletion, name a reviewer and response process. Do not claim a schedule before it exists. |
| Reporting reliability | Test real success, denied write, offline and timeout cases against the intended backend; verify reviewer access and abuse controls. No production database changes were made here. |
| Data Safety | Review Firebase Analytics/Crashlytics collection, identifiers, diagnostics, reports, web/weather, downloads and MCP against actual network behavior. User-initiated exceptions must be checked against the form instructions. |
| Model/dependency rights | Run `:app:releaseDependencyInventory`, then `scripts/build_license_inventory.py app/build/reports/release/dependencies.txt`. Inspect every component's actual LICENSE/NOTICE and applicable redistribution terms. POM names and this inventory are not clearance. Review model hosts, conversions, fonts, icons, imagery, API commercial-use terms, and source ownership. |
| Terms | Publisher/counsel review of consumer terms, acceptable use, AI limitations, external actions, third-party services and applicable jurisdiction. Repository source-code LICENSE is not consumer terms. |
| AI safety | Evaluate every shipped/recommended model: child exploitation, non-consensual sexual content, violence, fraud, self-harm, multilingual paraphrases, prompt injection through images/documents/tools. Test benign educational counterexamples. A flag button and instructions alone do not close this gate. |
| Release AAB | Build with approved signing key, inspect merged manifest, run minified release tests and Play pre-launch reports. Current debug tests do not establish release compatibility. |
| Device matrix | Fresh install, model onboarding/download/resume, denied permissions, offline behavior, low RAM/storage, background interruption, MCP confirmations, privacy/reporting, and 16 KB runtime. Avoid destructive tests on a personal device without a disposable test profile. |
| Play Console | App signing, current target API eligibility, Data Safety, content rating, target audience, foreground-service declarations, closed-testing requirements and country availability. Not verified in this repository. |

## Source references

- User data: https://support.google.com/googleplay/android-developer/answer/10144311
- AI content: https://support.google.com/googleplay/android-developer/answer/13985936
- 16 KB testing: https://developer.android.com/guide/practices/page-sizes
- Older Gemma terms: https://ai.google.dev/gemma/terms
- Gemma 4 license: https://ai.google.dev/gemma/docs/gemma_4_license

Recheck policy dates in Play Console before submission; deadlines and requirements can change.

## Validation recorded in this change

- 56 focused unit tests passed, including report timeout failure and location permission behavior.
- Debug APK build and release lint passed. Lint: 0 errors, 274 warnings, 2 hints; warnings are not claimed resolved.
- Signed/minified release AAB built successfully with existing local credentials. No upload/publication occurred.
- Rebuilt debug APK and release AAB pass ELF alignment checks; debug APK passes `zipalign -c -P 16 -v 4`.
- Debug APK installed and launched on emulator `sdk_gphone16k_x86_64` with `getconf PAGE_SIZE = 16384`. Privacy/settings/license-viewer Maestro flow passed. Full model inference on that emulator is not yet tested.
- Release OSS notice generation produced 260 entries. Resolved inventory: 302 components; local POM audit found 58 without license metadata. Metadata and notices do not establish legal clearance.
- Read-only Firebase database check for the configured project returned HTTP 403: Firestore API disabled or never enabled. Actual reporting delivery, report review, retention, deletion and abuse controls are NOT verified. After owner authorization, Firestore API enablement was verified live; the project has no databases. Database edition/location selection is pending before provisioning.
- Owner intends initial US distribution, is considering a personal publisher with Chevere AI branding, and plans a dedicated email. Those are not a finalized verified identity/contact or trademark clearance.

- Reporting presentation follow-up: 7 focused tests passed, including a failed report never showing the submitted toast. These overlap the earlier test set.
- Final signed release APK and AAB rebuilt after the reporting UI correction; release APK ZIP alignment and AAB ELF alignment pass.

## Backend provisioning follow-up

Owner approved `aiassistant-88f75`, Standard `(default)`, `us-central1`. Firestore API is enabled and the database was created and verified as Standard Native in that region. It starts with closed rules. No report collection data or retention policy was created. Local rules now deny report writes until verified intake is configured, because the old schema-only rule allowed unauthenticated submissions and quota/cost abuse. Reporting will correctly show failure in this state; production release remains gated. A protected intake design, app verification/abuse controls, reviewer access and retention/deletion must be completed before enabling it.

The final minified release APK was installed on the disposable 16 KB emulator and the Settings/privacy/license-viewer Maestro flow passed again. Model inference on that emulator remains unverified.

## Current handoff status

- Standard Native `(default)` database in `aiassistant-88f75`, `us-central1`, is provisioned by explicit owner choice.
- Fail-closed rules were tested with a local demo-project emulator and deployed. Catalog reads succeed; catalog writes, report reads/writes and unknown-path reads are denied. Reports intentionally remain unavailable until protected intake is implemented. This is a production blocker, not a completed reporting feature.
- Minified signed release APK: installed/launched on the disposable 16 KB emulator; Settings/privacy/OSS-license Maestro flow passed. Generated release notices have 260 entries. Signed release AAB and release APK builds pass, as do alignment checks.
- Reporting follow-up tests passed: the client cannot show a submission success toast after a failed repository result. Final lint count: 0 errors, 274 warnings, 2 hints.
- Do not publish privacy/terms yet: verified publisher remains missing; the owner-selected contact email is recorded below, and the 90-day retention proposal is unanswered. No retention/deletion automation or Play Console settings were changed. No Play Store or hosting publication occurred.

## Contact update

Owner selected `nehemias.miranda.work@gmail.com` for app support and privacy requests. The privacy source and consumer-terms draft now use this address instead of the unverified `support@chevere.ai` placeholder. No verification email was sent and mailbox delivery is not independently tested. Play Console verification is pending according to the owner. Policy/terms remain unpublished; publisher identity, audience, retention/deletion, protected report intake and other production gates remain open.

### November 2 MiniCPM experiment

The experimental MiniCPM engine adds pinned native source and a separately downloaded model/expert/vision bundle. November 1 artifact hashes in release-evidence.json describe the earlier build. See docs/minicpm-v4.7-testing.md for the runtime, license, hardware and validation limits. Existing reporting, audience, identity, safety and Play gates remain open; no production release is authorized by a successful model smoke test.

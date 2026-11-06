# Release review — November 2, 2026

This is an evidence ledger, not a compliance certificate or legal opinion. Review remaining operational items before applying for production access after closed testing.

## Implemented controls

- Production signing no longer falls back to the debug key. `validateSigningRelease` rejects missing signing configuration. Do not commit signing secrets.
- Privacy source (`public/privacy.html`, deployed to `https://aiassistant-88f75.web.app/privacy.html`) and onboarding disclose optional network tools, MCP arguments, reports, Play Integrity attestation, 90-day report retention, 18+ target audience, verified publisher identity (`Nehemias Miranda`, operating `Chevere AI`), and external destinations. Conversation deletion is distinguished from clearing all local app data.
- Remote MCP confirmation explains the data transfer and third-party privacy handling.
- Nonfatal telemetry no longer sends raw throwable messages/causes. Fatal crash capture and all other SDK data still need review.
- AI safety instructions cover direct chat, vision, agent runs, and suggestion prediction. Instructions are not a sufficient output safety system.
- Report timeouts return failure instead of success; ordinary coroutine cancellation propagates.
- `AiContentReportRepository` attaches a 90-day `expiresAt` timestamp to submitted reports. `firestore.rules` enforces strict create-only schema/size/timestamp/90-day-TTL validation on `ai_content_reports` and denies all client reads, updates, and deletes.
- Firebase App Check is integrated via variant-isolated initializers (`PlayIntegrityAppCheckProviderFactory` in `src/release`, `DebugAppCheckProviderFactory` in `src/debug`) so debug provider code is excluded from release binaries.
- `scripts/check_native_alignment.py ARTIFACT` validates 64-bit ELF load-segment alignment. ZIP alignment and a real 16 KB device/emulator test are separate requirements.
- The obsolete MediaPipe GenAI dependency was removed after its native library failed alignment checks.
- Bundled Gemma 4 catalog entries use Apache-2.0; older Gemma entries retain their separate terms. Conversion repository notices and dynamically discovered models still require individual review.

## Additional findings

- Verified Play Console personal developer account: **Nehemias Miranda** (operating **Chevere AI**), initial territory: **United States**, target audience: **18+**, support/privacy email: `nehemias.miranda.work@gmail.com`.
- Local release keystore certificate details:
  - Subject / Issuer: `CN=Nehemias Miranda, OU=Neo, O=Neo, L=New York, ST=New York, C=1`
  - `SHA-1`: `7B:FF:5E:3E:27:80:68:C3:1E:DC:F0:1D:BE:23:1F:A5:65:BF:33:C6`
  - `SHA-256`: `C2:00:0C:D5:EC:BC:1A:46:68:F5:8B:F7:37:DB:0C:A6:71:9F:B2:D9:E7:E7:9B:54:FA:9B:62:8C:B3:8C:31:2E`
- Google OSS Licenses is integrated into Settings through MVI for packaged dependency notices.

## Remaining operational steps in Firebase & Play Console

| Step | Action required |
|---|---|
| Firebase App Check (Play Integrity) | In Firebase Console (`Build > App Check > Apps`), register `dev.neo.chevereai` with Play Integrity using the upload/Play App Signing SHA-256 certificate, and enable Firestore enforcement once verified in closed testing. |
| Firestore TTL policy | In Google Cloud / Firebase Console (`Firestore > Time-to-live`), enable the TTL policy on collection group `ai_content_reports` field `expiresAt` so expired reports are automatically deleted after 90 days. |
| Play Console Closed Testing (Personal Account Gate) | Per [Google Play personal account requirements](https://support.google.com/googleplay/android-developer/answer/14151465), upload the signed release AAB (`app/build/outputs/bundle/release/app-release.aab`) to a Closed Testing track and run a closed test with at least **12 testers opted-in continuously for 14 days** before applying for Production access. |
| Play Console Declarations | Complete Privacy Policy URL (`https://aiassistant-88f75.web.app/privacy.html`), Data Safety (using `docs/data-safety-draft.md`), Target Audience (`18+`), Content Rating (IARC), App Access, Advertising ID (No), and Foreground Service (`dataSync` for user-initiated AI model downloads) declarations. |

## Source references

- Personal account requirements: https://support.google.com/googleplay/android-developer/answer/14151465
- User data: https://support.google.com/googleplay/android-developer/answer/10144311
- AI content: https://support.google.com/googleplay/android-developer/answer/13985936
- 16 KB testing: https://developer.android.com/guide/practices/page-sizes
- Older Gemma terms: https://ai.google.dev/gemma/terms
- Gemma 4 license: https://ai.google.dev/gemma/docs/gemma_4_license

## Validation ledger

- `AiContentReportFailureTest` passed (verifies timeout failure handling and 90-day `expiresAt` + scrubbed payload fields).
- Local Firestore emulator test (`scripts/check_report_rules.cjs`) passed:
  - `GET /models`: `200`, `POST /models`: `403`
  - Unvalidated `POST /ai_content_reports`: `403`
  - Valid `COMMIT /ai_content_reports` (with `createdAt == request.time` and 90-day `expiresAt`): `200`
  - `GET`, `PATCH`, `DELETE` on `/ai_content_reports/{id}`: `403`
  - Invalid TTL window (e.g., 365 days) `COMMIT /ai_content_reports`: `403`
- Updated `firestore.rules` compiled with 0 warnings and deployed to `aiassistant-88f75`.
- Updated `public/privacy.html` deployed to `https://aiassistant-88f75.web.app/privacy.html` and verified live.

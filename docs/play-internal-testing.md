# Play internal testing

Developer-account verification was confirmed by the owner on November 2, 2026. The Play app and first AAB are not set up yet. This workflow is ready to build an artifact; automated uploads additionally need the Console/API setup below. Account verification does not establish app policy approval or signing-certificate registration.

## Versioning

`version.properties` is the source for the visible major/minor/patch version, starting at **0.1.0**. Use canonical integers: 0.1.0 rather than 0.01.0. Change patch for fixes, minor for new pre-1.0 milestones, and major when intentionally moving to the next major version. The workflow does not automatically decide semantic-version bumps.

Android's separate integer `versionCode` is generated at build start as UTC seconds since January 1, 2026. For example, a build can be named 0.1.0 while its code is 26448010. Manual release jobs are serialized; rerunning later generates a new code even if the visible version stays the same. The uploader checks existing bundles/tracks and refuses duplicates or backwards codes. Play remains the final authority on previously used codes. Clock rollback or an out-of-band higher code causes failure; do not change an already built AAB's identity or silently bypass that check. The generator refuses codes outside 1..2100000000.

Local debug builds default to code 1 and use the same reviewed version name. Local release candidates should supply a generated `-PplayVersionCode=...`; do not upload a default-code development artifact.

## Manual workflow

GitHub Actions → **Play internal testing** → Run workflow:

- `build_only` (default): validate localization, run publishing-guard tests and release lint, build a signed AAB, check native alignment, and keep AAB/version JSON for three days. It uses the existing Google Services and signing secrets, makes no Play API calls, and can validate a branch.
- `upload_draft`: perform the same build and upload an **internal draft**. It does not make that draft available to testers.
- `release_internal`: upload and commit a completed release to the **internal** track. Console requirements and enrolled testers still determine availability.

Publishing modes run only from main. There is no production track option, automatic push/tag trigger, emulator, Maestro or model inference in CI. The uploader does not modify screenshots, store listings, countries, testers or production. Draft uploads preserve any completed internal release; an existing internal draft causes a clear refusal so it cannot silently be overwritten. It checks the AAB's JAR signature and expected upload-certificate SHA-256 before API access; Play's returned binary version code must match before the edit is committed. Resumable media chunks can retry, but commit is not automatically retried. If commit times out, check Console before rerunning because its outcome can be uncertain.

## First Console setup

1. Create the Chevere app in your verified Play Console account. Keep package **dev.neo.chevereai**; the uploaded bundle establishes the package identity. Choose the appropriate app details and complete whichever Console declarations it requests. This workflow does not infer an audience or publish policy drafts.
2. Run `build_only` on the intended merged commit and download its artifact. Corrective PR #14 brought MiniCPM onto main; build from a commit that includes it.
3. In Console → Testing → Internal testing, create the first release and upload that AAB manually. Follow Play App Signing enrollment. Preserve the existing upload keystore; do not replace it with a debug key or casually generate a new one.
4. Check App integrity → App signing → **Upload key certificate**. Copy its SHA-256 fingerprint into repository variable `PLAY_UPLOAD_CERT_SHA256`. This is the upload certificate, not Google's app-signing certificate. The local certificate previously observed is `c2000cd5ecbc1a4668f58bf737db0ca6719fb2d9e7e79b54fa9b628cb38c312e`; it has not yet been compared with Console.
5. Configure the internal tester list or Google Group in Console and use its opt-in link. A draft upload alone does not enroll or distribute to testers. Internal testing is distinct from any closed-testing requirement for production access.

Debug installs and Play-signed installs can have different certificates and cannot necessarily update one another. Use a separate test profile/device or plan data export before changing installs. The pipeline never uninstalls the current device app or deletes its models/documents.

## API access without a long-lived JSON key

Use Google Workload Identity Federation with GitHub OIDC and a dedicated service account, for example `chevere-play-publisher` in the existing aiassistant-88f75 project. Enable the Google Play Android Developer API in that project. These cloud/Console changes have not been provisioned by this PR.

- Create a GitHub OIDC provider using issuer `https://token.actions.githubusercontent.com`. Restrict its attribute condition to this repository and `refs/heads/main`; prefer immutable repository/owner IDs in addition to names. Do not trust every repository in a pool.
- Grant only that repository principal `roles/iam.workloadIdentityUser` on the dedicated service account. Do not grant project Owner/Editor for publishing.
- Invite the service account's email in Play Console Users and permissions. Grant access to Chevere only, with view and test-track release permissions. Do not grant production-release, financial or account-admin access.
- Set repository variables `PLAY_WIF_PROVIDER` (full provider resource name), `PLAY_SERVICE_ACCOUNT` (email), and `PLAY_UPLOAD_CERT_SHA256` (verified upload certificate).

Existing repository secrets are reused: `GOOGLE_SERVICES_JSON` and `RELEASE_KEYSTORE_BASE64` are base64-encoded; `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` are plain secret values. There is no new Play private-key secret. Google auth creates short-lived credentials for the upload step and cleans them up afterward.

The Python publishing tool uses Google's official API client, outside the Android app. No Ruby/Fastlane or AGP-coupled publishing plugin is added. `scripts/requirements-play.txt` pins the resolved upload dependencies; they are installed only for manual publishing modes. Run local guard tests with `python -m unittest discover -s scripts/tests -p 'test_*.py'`. Local uploads can use scoped Application Default Credentials or an explicitly configured credential file; never commit credentials or paste them into chat.

The production readiness ledger remains separate and blocked on its unresolved evidence. An internal upload is not Play approval, legal/license clearance, validated moderation or protected reporting intake.

Sources: [Google API setup](https://developers.google.com/android-publisher/getting_started), [internal testing](https://support.google.com/googleplay/android-developer/answer/9845334), [Android versioning](https://developer.android.com/studio/publish/versioning), [Google GitHub OIDC action](https://github.com/google-github-actions/auth).

## Local validation

A signed 0.1.0 AAB with code 26448010 was built from main plus this versioning change. Its protobuf manifest identity, JAR signature against the existing local upload key, eight native library alignments, and release lint (zero errors) were checked. Thirteen Python guard tests pass and the official SDK request shape was checked offline. No Play upload, cloud IAM setup, certificate match with Console, or GitHub-hosted workflow run has been performed. The local candidate is retained under the ignored distribution/ directory; see play-internal-validation.json for its SHA-256.

# Data Safety working inventory — not a submitted declaration

Initial intended distribution: United States. Verified Play Console personal publisher: **Nehemias Miranda** (operating **Chevere AI**). Target audience: **18+**. Owner-selected support/privacy contact: `nehemias.miranda.work@gmail.com`. Submitted AI content reports carry a **90-day** retention/TTL window (`expiresAt`). Reconcile this inventory against the final release and current Console questions before submitting the Play Console Data Safety form.

| Feature | Data and destination | Control / review |
|---|---|---|
| Chat, attached-image inference, suggestions | Local prompts, responses, images, recent conversation | No model-provider upload for core inference. Android speech recognition is a separate feature. |
| Local library | Imported text chunks, chat history, tasks, invoices, model files | App-private storage. Chat deletion removes that session, not the entire library. Clearing app storage/uninstall removes private files; exports and remote submissions are separate. |
| Voice input | Audio handled by configured Android speech-recognition provider | Offline requested, not guaranteed. Disclose provider/network behavior before permission. |
| Ambient sound | Microphone samples used locally for estimated noise level | Microphone permission; not a calibrated or certified meter. |
| Weather | Coordinates/place name to weather/geocoding provider; recent coordinates cached privately | Location permission and direct request. Audit precision and provider terms. |
| Contacts/calendar | On-device contacts and calendar lookup/write; drafts handed to other apps | Permissions and user request. Selected data can leave through sharing, mail/calendar apps, or confirmed remote tool arguments. |
| MCP | Tool arguments to user-selected server, potentially including sensitive data | Explicit confirmation shows destination and arguments. External server has independent practices; arbitrary endpoints remain an additional release risk. |
| Model catalog/download | IP/network metadata and chosen model requests to catalog/model hosts | Firebase/Hugging Face/other configured hosts. No assumption that all downloaded models share one license. |
| Analytics/Crashlytics/App Check | Installation identifiers, Play Integrity attestation tokens, usage/performance events, crash/diagnostic data | Nonfatal app logging is reduced to categories; fatal reports need separate examination. App Check uses Play Integrity in release builds. |
| AI-content report | Selected response excerpt, optional notes, model, reason, app version, creation timestamp, and 90-day expiration timestamp | User initiated; best-effort PII scrubbing does not guarantee anonymity. Protected by schema-validated Firestore rules + Firebase App Check (Play Integrity) and 90-day TTL expiration (`expiresAt`). |
| Android backup | Shared-preference domain only under current rules | Does not establish a deletion guarantee for information held by other apps/providers. Verify merged rules in release. |

Account deletion rules must be reassessed if accounts are introduced. Currently local data controls do not delete information submitted to third parties. Do not answer “no data collected” solely because inference is local.

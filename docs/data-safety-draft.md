# Data Safety working inventory — not a submitted declaration

Initial intended distribution: United States. Owner-selected support/privacy contact: nehemias.miranda.work@gmail.com. Mailbox delivery has not been independently tested. Publisher legal identity, audience/age group, report retention and Play Console verification remain unresolved. Reconcile this inventory against the final release and current Console questions before answering them.

| Feature | Data and destination | Control / review |
|---|---|---|
| Chat, attached-image inference, suggestions | Local prompts, responses, images, recent conversation | No model-provider upload for core inference. Android speech recognition is a separate feature. |
| Local library | Imported text chunks, chat history, tasks, invoices, model files | App-private storage. Chat deletion removes that session, not the entire library. Clearing app storage/uninstall removes private files; exports and remote submissions are separate. |
| Voice input | Audio handled by configured Android speech-recognition provider | Offline requested, not guaranteed. Disclose provider/network behavior before permission. |
| Ambient sound | Microphone samples used locally for estimated noise level | Microphone permission; not a calibrated or certified meter. |
| Weather | Coordinates/place name to weather/geocoding provider; recent coordinates cached privately | Location permission and direct request. Audit precision and provider terms. |
| Search | Search query to configured proxy/search provider | Model-generated queries can contain conversation data. Review third-party collection and applicable user-initiated transfer exceptions. |
| Contacts/calendar | On-device contacts and calendar lookup/write; drafts handed to other apps | Permissions and user request. Selected data can leave through sharing, mail/calendar apps, or confirmed remote tool arguments. |
| MCP | Tool arguments to user-selected server, potentially including sensitive data | Explicit confirmation shows destination and arguments. External server has independent practices; arbitrary endpoints remain an additional release risk. |
| Model catalog/download | IP/network metadata and chosen model requests to catalog/model hosts | Firebase/Hugging Face/other configured hosts. No assumption that all downloaded models share one license. |
| Analytics/Crashlytics | Installation identifiers, usage/performance events, crash/diagnostic data | SDK collection/retention settings and jurisdictional consent remain to be verified. Nonfatal app logging is reduced to categories; fatal reports need separate examination. |
| AI-content report | Selected response excerpt, optional notes, model, reason, app version and timestamp | User initiated; best-effort PII scrubbing does not guarantee anonymity. Retention/deletion/review ownership must be implemented and verified. |
| Android backup | Shared-preference domain only under current rules | Does not establish a deletion guarantee for information held by other apps/providers. Verify merged rules in release. |

Account deletion rules must be reassessed if accounts are introduced. Currently local data controls do not delete information submitted to third parties. Do not answer “no data collected” solely because inference is local.

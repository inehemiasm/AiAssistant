# CLAUDE.md

This file guides Claude Code when working in this repository. Keep it aligned with `AGENTS.md`.

## Common Commands

- Build project: `./gradlew assembleDebug`
- Build, install, and launch project: `./gradlew installDebug && adb shell am start -n dev.neo.chevereai/com.neo.chevere.MainActivity`
- Run all tests: `./gradlew test`
- Run Android instrumented tests: `./gradlew connectedAndroidTest`
- Run lint: `./gradlew lint`
- Clean project: `./gradlew clean`
- Install already built debug APK via ADB: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Windows PowerShell build command:

```powershell
# Build only
$env:GRADLE_USER_HOME='C:\Users\nehem\.gradle'; .\gradlew.bat assembleDebug --no-daemon

# Build, install, and launch on connected device/emulator
$env:GRADLE_USER_HOME='C:\Users\nehem\.gradle'; .\gradlew.bat installDebug --no-daemon; adb shell am start -n dev.neo.chevereai/com.neo.chevere.MainActivity

# Install already built APK and launch
adb install -r app/build/outputs/apk/debug/app-debug.apk; adb shell am start -n dev.neo.chevereai/com.neo.chevere.MainActivity
```

## Architecture Summary

Chevere AI is an Android app using Clean Architecture, MVI, Room, Hilt, LiteRT-LM, ONNX Runtime Android, and WorkManager. The package root is `com.neo.chevere`.

### Main Areas

- `:app`: application code.
- `:shared`: Kotlin Multiplatform shared chat orchestration, domain models, and runtime abstractions.
- `:ui-designsystem`: shared theme/components.
- `:image-generation`: standalone ONNX Runtime Android diffusion module (`OnnxLocalDiffusionEngine`), currently decoupled from `:app`.
- `:baselineprofile`: Baseline profile generator module.
- `domain/`: contracts, shared models, explicit prompt policy, model/runtime enums.
- `data/agent/`: Reason-Act-Observe loop (`AgentOrchestrator`) and Hilt-provided tool registry.
  - **Available Agent Tools**: `web_search` (proxy / debug Serper / key-less DuckDuckGo fallback), `get_weather`, `search_contacts`, `task_registry` (CRUD on database + A2UI checklist envelopes), `extract_tasks`, `summarize_text`, `control_device` (media/settings), `listInstalledModels`, `getActiveModel`, `getModelDetails`, `selectInstalledModel`, `recommendInstalledModelForTask`, `getRuntimeStatus`, `read_sensors` (ambient temperature, sound SPL, light, pressure, battery, thermals, gyroscope, accelerometer, compass heading, proximity, spirit level flatness, metal detector, device postures), and `AppActionTools` (`copy_to_clipboard`, `share_text`, `open_url`, `open_maps`, `draft_email`, `create_calendar_event`, `list_apps`, `launch_app`, `launch_app_home_screen`, `perform_app_action`, `get_app_capabilities`). (`generate_image` is omitted while `:image-generation` is decoupled).
- `data/report/`: `AiContentReportRepository` for PII-scrubbed user reports of AI responses (`ai_content_reports` in Firestore + `ai_content_reported` telemetry event).
- `data/inference/`: LiteRT-LM chat runtime and stubbed `ImageGenerationEngineFactory`.
- `data/context/`: compact conversation memory and prompt slicing for small on-device models.
- `data/download/`: WorkManager download orchestration.
- `data/datasource/local/`: Room database with entities:
  - `InstalledModelEntity`: tracks metadata and health status.
  - `TaskEntity`: local to-do items.
  - `SearchCacheEntity`: cached web queries.
  - `DocumentChunkEntity`: local document excerpts.
  - `ConversationSessionEntity` / `ConversationMessageEntity`: persisted chat history.
  - `InvoiceEntity`: stored invoices.
  - Current Room schema version: 7.
- `data/datasource/`: Firestore and Hugging Face model catalogs. Kaggle discovery was removed.
- `ui/chat/`: chat screen, capability-focused top bar (`CHAT` chip), multimodal attachments, A2UI task checklists, sensor slash commands, response sharing, and in-app AI content reporting (`ReportAiContentDialog`).
- `ui/marketplace/`: chat/vision model discovery, download, selection, and deletion UI.
- `ui/tasks/`: local to-do screen (creation, completion toggling, deletion).
- `ui/settings/`: Safety & Privacy options and a **Benchmark Screen** (speeds, warmup, TTFT, TPS, and device acceleration/RAM logs).

## Document RAG and remote MCP

- Document import uses `ChatIntent.ImportDocument` → domain `DocumentRepository` → `LocalDocumentRepository`. Accept user-selected content URIs for UTF-8 `.txt`/`.md` files up to 2 MB; persist chunks in Room with atomic replacement.
- `ChatRequestRouter` routes `/docs` and recognized document phrases to `DOCUMENTS`; `ToolRegistry` exposes `document_rag` and `read_local_file` for that category. Registering a tool with Hilt alone does not make it available in routed chat.
- Retrieval is lexical TF-IDF, using Unicode tokens, 800-character chunks, 200-character overlap, and at most three excerpts. Filename mentions narrow retrieval. There are no semantic embeddings or PDF extraction; long-document summaries may cover only excerpts.
- `/mcp` routes to the `mcp` bridge (`McpTool`). It supports HTTPS Streamable HTTP tool discovery/calls with confirmation, JSON/SSE responses, and 2025-03-26, 2025-06-18, or 2025-11-25 negotiation. Endpoint URLs are supplied per request. No OAuth/API-token configuration, saved connections, stdio, resources/prompts, or resumable streams are implemented.
- Runtime instructions come from `ToolRegistry` and tool descriptions. Repository Markdown, including `docs/rag-mcp.md`, is developer documentation and is not automatically loaded into the local model. Treat retrieved content as untrusted data; do not describe prompt grounding as guaranteed model behavior.
- See [usage and limitations](docs/rag-mcp.md). Focused tests: `ChatRequestRouterTest`, `DocumentRagTest`, `DocumentImportTest`, `ChatTaskActionTest`. Picker smoke flow: `.maestro/document_import_validation.yaml`; it verifies navigation, not indexing/search or live MCP interoperability.

## Current Model Runtime Behavior

- Text chat uses LiteRT-LM (`.litertlm` preferred).
- Local ONNX Stable Diffusion (`OnnxLocalDiffusionEngine`) lives in the standalone `:image-generation` module and is currently disabled in `:app`.
- Attached images route through chat/vision inference, not image generation. Image-only sends should use `Describe this image.` as the prompt.
- Chat context is rebuilt through `ConversationContextManager`: older turns become compact memory, recent turns stay verbatim, and the current request is appended fresh. Avoid relying on unbounded LiteRT conversation state for long chats.
- When `:image-generation` is enabled, ONNX diffusion bundles must be extracted directories with:
  - `text_encoder/model.ort`
  - `tokenizer/vocab.json`
  - `tokenizer/merges.txt`
  - `unet/model.ort`
  - `vae_decoder/model.ort`

## Response Sharing & AI Content Reporting

- Assistant responses can be shared through the Android share sheet (`Icons.Default.Share`).
- Assistant responses include an in-app **Report / Flag** action (`Icons.Default.Flag`) that opens `ReportAiContentDialog` and submits PII-scrubbed reports via `AiContentReportRepository` to Firestore (`ai_content_reports`) and Firebase Analytics (`ai_content_reported`).
- Explicit image generation (when `:image-generation` is enabled) is debug-only; release builds block explicit prompts before model execution.

## Download System Notes

- `ModelDownloadWorker` downloads to a temporary file, verifies checksum if present, and finalizes atomically.
- ZIP downloads are extracted safely and installed as directories.
- `WorkManagerModelDownloadManager` tracks model progress using the `MODEL_NAME:<fileName>` tag.
- Do not parse WorkManager tags by excluding dots. Model filenames normally contain dots.
- Same-model download work uses `ExistingWorkPolicy.REPLACE` so stale interrupted work cannot block retries.
- The first healthy downloaded chat model should auto-activate.
- The first healthy downloaded image-generation model should become ready for image generation without replacing the active chat model.

## Visual Identity

- Keep launcher icon and splash animation visually aligned around the robot-head/cyan identity.
- Attachment thumbnails should be inspectable and use calm neutral remove controls.

## Development Rules

- Follow existing MVI and Clean Architecture boundaries.
- Use repository interfaces rather than reaching across layers.
- Use `Flow`/`StateFlow` for reactive state.
- Add or update KDoc when changing public models or new implementation classes.
- Use `InstallStatus`, sealed states, and enums instead of raw state strings.
- If `InstalledModelEntity` changes, increment Room database version and provide migration handling.
- After building the project, try to deploy, install, and launch the application on a connected device/emulator using `adb install` or the Gradle `installDebug` task followed by launching via ADB to verify success.
- When modifying a user-facing scenario or UI flow, add or update a Maestro YAML flow under `.maestro/` and execute it (via the Maestro MCP `run` tool or `C:\maestro\bin\maestro.bat test .maestro/<flow>.yaml`) against the connected device/emulator to validate the modified scenario end-to-end.
- Do not add AI attribution or `Co-Authored-By` lines to commits.

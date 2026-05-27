# Chevere AI: Local AI Assistant

Chevere AI is a privacy-first Android assistant that runs local language models on device and can route user requests through a small agent/tool system. It supports chat, image attachments, Android actions, model marketplace management, and local text-to-image generation through ONNX Stable Diffusion bundles.

## Features

- **Local LLM execution**: Runs LiteRT-LM models such as Gemma from app-private storage.
- **Multimodal chat**: Users can attach gallery or camera images when the active chat model supports vision. Image attachments always route through chat/vision inference rather than the image-generation backend.
- **Rich Math Formatting**: Prettifies math equations, derivatives, functions, and integrals. Inline (`$...$`) and block (`$$...$$`) LaTeX are parsed and formatted using serif typography, custom symbols, and specific styling for easy readability.
- **Efficient context slicing**: Chat uses a rolling memory pattern for small on-device models, keeping recent turns verbatim while compacting older turns into a short deterministic summary.
- **Agent workflow**: `AgentOrchestrator` runs a Reason-Act-Observe loop and executes registered tools.
- **Everyday Sensor Utilities**:
  - **Spirit Level (Flatness)**: Computes pitch and roll angles in degrees via the Accelerometer to check if a table/surface is level.
  - **Metal & Magnet Detector**: Detects nearby metallic objects, studs, or magnets using Magnetometer strength ($\mu\text{T}$).
  - **Device Posture**: Detects physical placement like Face Up, Face Down, Portrait, Landscape, or Tilted.
- **Environment & Hardware Sensors**:
  - **Ambient Sound Level**: Estimates noise levels in decibels (dB SPL) via microphone amplitude (requires `RECORD_AUDIO` permission).
  - **Motion & Rotation**: Queries the Gyroscope (rad/s) and Accelerometer (m/s²) to identify if the device is stationary, rotating, or shaking.
  - **Compass Heading**: Computes heading degrees ($0^\circ$-$360^\circ$) and cardinal directions (e.g. North-East) using accelerometer + magnetometer sensor matrix.
  - **Light, Pressure, & Temp**: Reads ambient light level (lux), barometric pressure (hPa), room temperature (where supported), battery details, and internal CPU temperatures/thermals.
- **Image generation**:
  - Agent tool: `generate_image` lets Gemma improve the prompt before calling the image backend.
  - Slash commands: `/image`, `/img`, and `/imagine` bypass Gemma and call image generation directly.
  - If no healthy local image model is installed, image requests show a download prompt instead of failing inside the agent loop.
  - Local ONNX diffusion runtime for extracted Stable Diffusion bundles.
- **Explicit image handling**:
  - Debug builds can gate explicit image prompts behind an age-verification dialog.
  - Release builds block explicit image generation before it reaches any model backend.
  - Debug explicit images are masked by default in chat and can be revealed or hidden with the visibility toggle.
- **Safety and privacy UX**:
  - Settings uses expandable Safety & Privacy rows for local processing, content controls, sharing, and local storage.
  - Assistant responses expose a share action through the Android share sheet. There is no in-app report mechanism yet.
- **Model marketplace**:
  - Merges curated/discovered Hugging Face models with Firestore catalog entries.
  - Separates chat/vision models from image-generation models for clearer selection.
  - Auto-activates the first usable chat model after download; the first image model becomes available immediately for image generation without replacing the chat model.
  - Uses Room as the installed-model source of truth.
  - Protects active models from deletion and tracks lifecycle states with `InstallStatus`.
- **Background downloads**:
  - WorkManager foreground downloads with Ktor streaming.
  - SHA-256 verification when a checksum is provided.
  - ZIP model bundles are extracted atomically with zip-slip protection.
  - Download progress is keyed by model filename using a stable `MODEL_NAME:` WorkManager tag.
- **Local Tasks Management**:
  - Organize checklists with automated task identification, listing, creation, status updating (pending/completed), and deletion in the database.
- **On-Device Performance Benchmarking**:
  - Runs local LLM performance tests measuring engine initialization/warmup latency, Time-To-First-Token (TTFT), tokens-per-second generation throughput (TPS), and logging device physical memory specs and hardware acceleration (CPU/GPU).

## Tech Stack

- **UI**: Jetpack Compose, Material 3, MVI
- **Architecture**: Clean Architecture plus agent/tool workflow
- **Dependency injection**: Hilt
- **Local LLM runtime**: Google AI Edge LiteRT-LM
- **Image generation runtime**: ONNX Runtime Android for Stable Diffusion style bundles
- **Database**: Room for installed models, tasks checklist, and offline search cache
- **Networking**: Ktor + OkHttp
- **Background tasks**: WorkManager foreground service
- **Modules**:
  - `:app`: Android application and feature implementation
  - `:ui-designsystem`: shared theme, typography, and UI primitives

## Architecture

### Domain

Located under `app/src/main/java/com/neo/chevere/domain/`.

- Shared models: `ChatMessage`, `InstalledModel`, `ModelEntry`, `ImageGenerationRequest`.
- Repository contracts: `ChatRepository`, `InstalledModelRegistry`.
- Prompt policy: `ExplicitImagePromptPolicy` owns deterministic explicit image preflight.

### Data

Located under `app/src/main/java/com/neo/chevere/data/`.

- `AgentOrchestrator`: Local tool loop and user action confirmation flow.
- `ToolRegistry`: Hilt-provided collection of agent tools:
  - **Core Tools**: `search_web`, `get_weather`, `summarize_text`, `model_registry`
  - **Local Tasks**: `task_registry` (CRUD actions on database), `extract_tasks`
  - **Device / Contacts**: `control_device` (volume, display/DND shortcuts), `search_contacts` (name/email lookup)
  - **Android App Integration**: `copy_to_clipboard`, `share_text`, `open_url`, `open_maps`, `draft_email`, `create_calendar_event`, `list_apps`, `launch_app`, `launch_app_home_screen` (`OpenAppTool`), `perform_app_action` (`OpenDeepLinkTool`), `get_app_capabilities`
  - **Image Tools**: `generate_image` (ONNX Stable Diffusion generation) and `analyze_image` (vision capabilities)
- `InferenceManager`: LiteRT-LM model lifecycle.
- `ConversationContextManager`: Compact memory and recent-turn prompt slicing for constrained on-device context windows.
- `ImageGenerationManager`: Chooses installed image-generation models and falls back across compatible engines.
- `OnnxLocalDiffusionEngine`: ONNX text encoder, tokenizer, UNet scheduler loop, VAE decode, and PNG persistence.
- `ModelDownloadWorker`: Downloads, verifies, extracts, and finalizes model files.

### UI

Located under `app/src/main/java/com/neo/chevere/ui/`.

- **Chat Screen** (`ui/chat/`): Uses `ChatState`, `ChatIntent`, and `SendState`. The top bar displays `CHEVERE AI` plus `CHAT` and `IMAGE` readiness chips. Rebuilds rolling context dynamically. Masks explicit images by default with a visibility toggle.
- **Marketplace Screen** (`ui/marketplace/`): Discovers, downloads, selects, and deletes models. Separates chat/vision models from image models.
- **Tasks Screen** (`ui/tasks/`): Renders local pending/completed checklist tasks. Supports adding new tasks, status toggling, and deletion.
- **Settings Screen** (`ui/settings/`): Safety & privacy configurations and theme customization.
- **Benchmark Screen** (`ui/settings/`): Runs speed tests measuring engine warmup, TTFT, TPS, and outputs hardware specs (available RAM, CPU/GPU acceleration).

## Model Formats

Supported installed model shapes:

- `.litertlm`: LiteRT-LM chat model.
- `.bin`: legacy/local LiteRT-compatible model file.
- Extracted ONNX diffusion directory:
  - `text_encoder/model.ort`
  - `tokenizer/vocab.json`
  - `tokenizer/merges.txt`
  - `unet/model.ort`
  - `vae_decoder/model.ort`

ZIP downloads are extracted under `context.filesDir/<zip-name-without-extension>`.

## Common Commands

```bash
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
./gradlew lint
./gradlew clean
```

On Windows PowerShell:

```powershell
$env:GRADLE_USER_HOME='C:\Users\nehem\.gradle'; .\gradlew.bat assembleDebug --no-daemon
```

## Setup

1. Add `google-services.json` to `app/` if Firestore catalog support is needed.
2. Build and install the debug APK.
3. Open **Models** and download or manually push a supported model bundle.
4. Download a LiteRT-LM model for chat. If it is the only chat model, it activates automatically.
5. Download an ONNX diffusion image model if you want image generation. If it is the only image model, it is ready immediately.
6. Use `/image your prompt` for direct local image generation.

## Notes

- Image generation is slow on CPU/mobile hardware and can take minutes.
- The ONNX diffusion path is experimental and quality depends heavily on the bundle format and scheduler compatibility.
- Explicit image generation is debug-only. Release builds block explicit image generation.
- Launcher icon and splash robot use the same robot-head/cyan visual identity.
- Network access is only required for model discovery, downloads, web search, weather, and other explicitly network-backed tools.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).

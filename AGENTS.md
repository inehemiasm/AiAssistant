# AGENTS.md

This file guides Codex and other coding agents working in this repository.

## Common Commands

- Build project: `./gradlew assembleDebug`
- Run all tests: `./gradlew test`
- Run Android instrumented tests: `./gradlew connectedAndroidTest`
- Run lint: `./gradlew lint`
- Clean project: `./gradlew clean`

On Windows PowerShell, prefer:

```powershell
$env:GRADLE_USER_HOME='C:\Users\nehem\.gradle'; .\gradlew.bat assembleDebug --no-daemon
```

## High-Level Architecture

The project is an Android application using Clean Architecture and MVI, enhanced with an agent/tool workflow, local model management, and local image generation.

### Modules

- `:app`: Main Android application module.
- `:ui-designsystem`: Shared design system, theme, typography, and reusable components.

### Package Root

Use `app/src/main/java/com/neo/chevere/`. Some older docs may mention `com.neo.aiassistant`; that path is stale.

### Domain Layer

Path: `app/src/main/java/com/neo/chevere/domain/`

- Shared models: `ChatMessage`, `InferenceRequest`, `ImageGenerationRequest`, `InstalledModel`, `ModelEntry`.
- Repository interfaces: `ChatRepository`, `InstalledModelRegistry`.
- Model enums: `ModelRuntime`, `ModelFormat`, `ModelTaskType`, `ModelCapability`, `InstallStatus`.
- Explicit prompt policy: `ExplicitImagePromptPolicy`.

### Data Layer

Path: `app/src/main/java/com/neo/chevere/data/`

- **Agent layer** (`data/agent/`):
  - `AgentOrchestrator`: Reason-Act-Observe loop.
  - `ToolRegistry`: Hilt-provided set of `AgentTool` implementations.
  - **Available Agent Tools:**
    - `ImageGenerationTool` (`generate_image`): Agent-facing Stable Diffusion tool. Gemma improves prompts before calling it.
    - `WebSearchTool` (`search_web`): Online web search.
    - `WeatherTool` (`get_weather`): Local and global weather info.
    - `SearchContactsTool` (`search_contacts`): Resolves local device contact emails by name.
    - `TaskRegistryTool` (`task_registry`): Manages the local to-do checklist database (create, list, update, delete).
    - `ExtractTasksTool` (`extract_tasks`): Extracts tasks from chat transcripts.
    - `SummarizeTextTool` (`summarize_text`): Compacts blocks of text locally.
    - `DeviceControlTool` (`control_device`): Modifies device settings (e.g., volume, display/DND shortcuts).
    - `ModelRegistryTools` (`model_registry`): Allows the agent to query active or installed models.
    - `SensorsTool` (`read_sensors`): Queries device environment and hardware sensors to check how hot/cold the room is or how bright it is. Includes ambient room temperature, device internal temperature, ambient light level (lux) / brightness, atmospheric pressure (hPa), battery level, charging status, and CPU thermal throttling status.
    - `AppActionTools`: Interacts with Android applications and actions:
      - `copy_to_clipboard` / `share_text` / `open_url` / `open_maps`
      - `draft_email` / `create_calendar_event`
      - `list_apps` / `launch_app` / `launch_app_home_screen` (`OpenAppTool`)
      - `perform_app_action` (`OpenDeepLinkTool` for specific app routes like Squarespace)
      - `get_app_capabilities` (scans intent/filter capacities of apps)
- **Inference runtime** (`data/inference/`):
  - `InferenceManager`: LiteRT-LM model loading and chat inference.
  - `ImageGenerationManager`: Selects compatible installed diffusion models.
  - `OnnxLocalDiffusionEngine`: Runs Stable Diffusion generation using extracted ONNX files.
- **Context management** (`data/context/`):
  - `ConversationContextManager`: Rolling context memory compressor for constrained on-device windows.
- **Data sources** (`data/datasource/`):
  - `CompositeModelCatalogDataSource`: Merges Hugging Face Hub discovery and Firestore catalogs.
- **Registry & Storage** (`data/datasource/local/`):
  - `RoomInstalledModelRegistry`: Registry for installed LLM/diffusion models.
  - `AppDatabase`: Room database containing tables:
    - `InstalledModelEntity`: Stores model metadata, filepath, and `InstallStatus`.
    - `TaskEntity`: Stores local task titles, descriptions, and `TaskStatus`.
    - `SearchCacheEntity`: Caches web queries to save offline bandwidth.
- **Downloads**:
  - `ModelDownloadWorker`: Downloads models with foreground WorkManager, checks SHA-256, and extracts ZIPs safely.
  - `WorkManagerModelDownloadManager`: Tracks downloads via `MODEL_NAME:<fileName>` tag. Do not reject filenames with dots.

### UI Layer

Path: `app/src/main/java/com/neo/chevere/ui/`

- **Chat Screen** (`ui/chat/`):
  - Chat MVI state lives in `ChatContract.kt`.
  - `ChatTopBar` is brand/capability focused. It shows `CHEVERE AI` with `CHAT` and `IMAGE` readiness chips, not the selected model filename.
  - Attached images must route through chat/vision inference. Do not route image attachments to the text-to-image backend unless the user explicitly invokes an image-generation flow without an attachment.
  - Image-only sends default to `Describe this image.`.
  - Chat context should go through `ConversationContextManager`. Do not add unbounded message replay directly into LiteRT conversations.
  - Assistant message actions should use share semantics. Do not reintroduce report/flag controls until a real reporting backend exists.
  - `SendState.GeneratingImage` drives `GENERATING IMAGE...` UI while slash commands run.
  - Slash commands `/image`, `/img`, and `/imagine` bypass Gemma and call `ChatRepository.generateImage`.
  - If no healthy image-generation model is installed, image requests should show `ChatEffect.ShowImageModelDownloadPrompt` instead of falling through to a tool error.
  - Explicit image requests show `AgeVerificationDialog` first.
  - Explicit generated images are masked by default using `ChatMessage.isExplicitImage` and `ChatMessage.isImageMasked`.
- **Marketplace Screen** (`ui/marketplace/`):
  - Marketplace state observes both Room-installed models and WorkManager download progress.
  - Marketplace UI should keep chat/vision models and image-generation models visually separated. Chat models can be selected as the active LLM; image models are used automatically by the image backend.
- **Tasks Screen** (`ui/tasks/`):
  - Displays pending and completed tasks.
  - Supports adding tasks through an `AddTaskDialog`, toggling completion, and deletion. Integrated with `TaskRegistryTool` at the data layer.
- **Settings & Benchmark** (`ui/settings/`):
  - Settings Safety & Privacy content remains expandable and factual: local processing, release controls, user-controlled sharing, and local storage.
  - **Benchmark Screen**: Runs local inference speed tests. Measures engine warmup time, Time-To-First-Token (TTFT), generation throughput (TPS), and logs physical hardware metrics (CPU/GPU acceleration, available RAM).

## Model Management Rules

- Active models cannot be deleted.
- Downloads and engine switches should not be interrupted by deletion.
- Installed model health is represented with `InstallStatus`; use existing enum values instead of raw strings.
- `ModelSource` describes where a model came from. Supported remote sources are Hugging Face and Firebase/Firestore; local disk scans classify installed files as `LOCAL`.
- When the first healthy chat model finishes downloading, it should auto-activate. When the first healthy image-generation model finishes downloading, it should become usable immediately without replacing the active chat model.
- Supported installed formats:
  - `.litertlm`
  - `.bin`
  - extracted ONNX diffusion directory
- ZIP downloads are extracted atomically into a directory named after the ZIP file without `.zip`.
- If modifying `InstalledModelEntity`, increment the Room database version and handle migration. Current setup uses destructive fallback.

## Image Generation Notes

- ONNX diffusion bundle must contain:
  - `text_encoder/model.ort`
  - `tokenizer/vocab.json`
  - `tokenizer/merges.txt`
  - `unet/model.ort`
  - `vae_decoder/model.ort`
- Generated images are written under the app's `generated_images` directory and exposed through `FileProvider`.
- Agent-triggered image generation returns the `CHEVERE_IMAGE_GENERATION_RESULT:` payload; `ChatViewModel` parses it into an image message.
- Direct slash-command image generation does not ask Gemma to rewrite the prompt.
- Agent tool image generation should ask Gemma to improve the prompt before `generate_image`.
- Explicit image generation is debug-only. Release builds block explicit prompts before the backend is invoked.

## Contacts Integration & Tool Usage

- **Natural Language Trigger**: Users trigger the contacts integration implicitly by asking the assistant questions or commands in natural language (e.g., "Find John's email in my contacts," "Do I have an email address for Alice?," or "Draft an email to Bob").
- **Agent Resolution Flow**:
  1. The user asks to email or contact someone by name rather than typing an email address.
  2. The agent invokes `search_contacts(query = "<Name>")` to fetch matching contact names and email addresses.
  3. If a match is found, the agent uses that email to proceed to another tool (e.g., `draft_email`) or prints the contact details directly to the user.
- **Runtime Permission Flow**:
  1. `SearchContactsTool` verifies whether `Manifest.permission.READ_CONTACTS` is granted.
  2. If not granted, the tool returns the error payload `CONTACTS_PERMISSION_REQUIRED`.
  3. The `ChatViewModel` translates this error to emit `ChatEffect.RequestContactsPermission`.
  4. `ChatScreen` intercepts this effect, displays the system permission dialog to the user, and prompts the user to grant read-contacts access. Once granted, subsequent requests automatically succeed.

## Visual Identity Notes

- Launcher icon and splash animation should use the same robot-head/cyan identity.
- Keep attachment previews large enough to inspect and use a neutral remove affordance rather than an oversized destructive red control.

## Development Guidelines

- Prefer existing patterns, MVI state, Hilt DI, and repository abstractions.
- Favor reactive state with `Flow` or `StateFlow`.
- Add or update KDoc for new implementations and changed public data models.
- Prefer sealed classes or sealed interfaces over raw booleans, strings, or ad-hoc UI logical evaluations (e.g. evaluating multiple conditions inside Compose layout blocks) to represent component states. Keep state calculations centralized in the MVI state or ViewModel layer so that UI components remain purely declarative.
- Keep model catalog filtering conservative and runtime-aware.
- Surface actionable errors with `InstallStatus` and `DownloadProgress.Error`.
- Do not add `Co-Authored-By` or other AI/agent attribution to commits.

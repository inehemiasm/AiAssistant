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
  - `ImageGenerationTool`: agent-facing text-to-image tool. Gemma should improve prompts before calling it.
- **Inference runtime** (`data/inference/`):
  - `InferenceManager`: LiteRT-LM model loading and chat inference.
  - `ImageGenerationManager`: selects a compatible installed image-generation model.
  - `OnnxLocalDiffusionEngine`: local ONNX Stable Diffusion style pipeline.
  - `QualcommImageGenerationEngine`: QNN bundle validation only; native QAIRT execution is not implemented.
- **Data sources** (`data/datasource/`):
  - `CompositeModelCatalogDataSource`: merges Firestore, Hugging Face, and Kaggle catalogs.
  - `HuggingFaceModelCatalogDataSource`: curated models plus Hub discovery.
  - `KaggleModelCatalogDataSource`: curated Kaggle/TFHub style models.
- **Registry** (`data/datasource/local/`):
  - `RoomInstalledModelRegistry`: source of truth for installed models.
  - `AppDatabase`: Room database.
- **Downloads**:
  - `ModelDownloadWorker`: foreground WorkManager download, checksum verification, ZIP extraction.
  - `WorkManagerModelDownloadManager`: tracks progress by `MODEL_NAME:<fileName>` tag. Do not reintroduce tag parsing that rejects dots in filenames.

### UI Layer

Path: `app/src/main/java/com/neo/chevere/ui/`

- Chat MVI state lives in `ChatContract.kt`.
- `SendState.GeneratingImage` drives `GENERATING IMAGE...` UI while slash commands run.
- Slash commands `/image`, `/img`, and `/imagine` bypass Gemma and call `ChatRepository.generateImage`.
- Explicit image requests show `AgeVerificationDialog` first.
- Explicit generated images are masked by default using `ChatMessage.isExplicitImage` and `ChatMessage.isImageMasked`.
- Marketplace state observes both Room-installed models and WorkManager download progress.

## Model Management Rules

- Active models cannot be deleted.
- Downloads and engine switches should not be interrupted by deletion.
- Installed model health is represented with `InstallStatus`; use existing enum values instead of raw strings.
- `ModelSource` describes where a model came from, but local disk scans currently classify installed files as `LOCAL`.
- Supported installed formats:
  - `.litertlm`
  - `.bin`
  - extracted ONNX diffusion directory
  - extracted QNN/Qualcomm image-generation directory
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

## Development Guidelines

- Prefer existing patterns, MVI state, Hilt DI, and repository abstractions.
- Favor reactive state with `Flow` or `StateFlow`.
- Add or update KDoc for new implementations and changed public data models.
- Use sealed classes, sealed interfaces, or enums for state. Avoid raw string state.
- Keep model catalog filtering conservative and runtime-aware.
- Surface actionable errors with `InstallStatus` and `DownloadProgress.Error`.
- Do not add `Co-Authored-By` or other AI/agent attribution to commits.

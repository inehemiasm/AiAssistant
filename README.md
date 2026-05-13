# Chevere AI: Local AI Assistant

Chevere AI is a privacy-first Android assistant that runs local language models on device and can route user requests through a small agent/tool system. It supports chat, image attachments, Android actions, model marketplace management, and local text-to-image generation through ONNX Stable Diffusion bundles.

## Features

- **Local LLM execution**: Runs LiteRT-LM models such as Gemma from app-private storage.
- **Multimodal chat**: Users can attach gallery or camera images when the active chat model supports vision. Image attachments always route through chat/vision inference rather than the image-generation backend.
- **Agent workflow**: `AgentOrchestrator` runs a Reason-Act-Observe loop and executes registered tools.
- **Image generation**:
  - Agent tool: `generate_image` lets Gemma improve the prompt before calling the image backend.
  - Slash commands: `/image`, `/img`, and `/imagine` bypass Gemma and call image generation directly.
  - If no healthy local image model is installed, image requests show a download prompt instead of failing inside the agent loop.
  - Local ONNX diffusion runtime for extracted Stable Diffusion bundles.
  - Qualcomm/QNN bundle detection is present, but native QAIRT execution is not implemented yet.
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

## Tech Stack

- **UI**: Jetpack Compose, Material 3, MVI
- **Architecture**: Clean Architecture plus agent/tool workflow
- **Dependency injection**: Hilt
- **Local LLM runtime**: Google AI Edge LiteRT-LM
- **Image generation runtime**: ONNX Runtime Android for Stable Diffusion style bundles
- **Database**: Room for installed models and search cache
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

- `AgentOrchestrator`: local tool loop and confirmation flow.
- `ToolRegistry`: exposes tools such as search, weather, app actions, clipboard/share, model inspection, and image generation.
- `InferenceManager`: LiteRT-LM model lifecycle.
- `ImageGenerationManager`: chooses installed image-generation models and falls back across compatible engines.
- `OnnxLocalDiffusionEngine`: ONNX text encoder, tokenizer, UNet scheduler loop, VAE decode, and PNG persistence.
- `QualcommImageGenerationEngine`: validates QNN bundle shape and returns a clear unsupported-runtime failure until QAIRT execution exists.
- `ModelDownloadWorker`: downloads, verifies, extracts, and finalizes model files.

### UI

Located under `app/src/main/java/com/neo/chevere/ui/`.

- Chat uses `ChatState`, `ChatIntent`, and `SendState`.
- The chat top bar is brand/capability focused: it shows `CHEVERE AI` plus `CHAT` and `IMAGE` readiness chips rather than a single selected model name.
- Chat model switching lives in the Models screen. Image-generation models are used automatically by `ImageGenerationManager`.
- Attached images force the chat/vision path. If the text field is empty, the prompt defaults to `Describe this image.`.
- Attached-image previews use a larger thumbnail with a neutral remove control.
- Slash-command image generation uses `SendState.GeneratingImage` so the UI shows `GENERATING IMAGE...`.
- Requests that look like image generation show an image-model download dialog when no healthy image model is installed.
- `AgeVerificationDialog` handles explicit prompt gating.
- Generated explicit images use `ChatMessage.isExplicitImage` and `ChatMessage.isImageMasked` to show a blur mask and reveal/hide toggle.
- Marketplace screens observe `allDownloadsProgress` and local model state.

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
- Qualcomm/QNN image generation directory:
  - `metadata.json`
  - `text_encoder.onnx`
  - `text_encoder_qairt_context.bin`
  - `unet.onnx`
  - `unet_qairt_context.bin`
  - `vae.onnx`
  - `vae_qairt_context.bin`

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

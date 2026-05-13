# CLAUDE.md

This file guides Claude Code when working in this repository. Keep it aligned with `AGENTS.md`.

## Common Commands

- Build project: `./gradlew assembleDebug`
- Run all tests: `./gradlew test`
- Run Android instrumented tests: `./gradlew connectedAndroidTest`
- Run lint: `./gradlew lint`
- Clean project: `./gradlew clean`

Windows PowerShell build command:

```powershell
$env:GRADLE_USER_HOME='C:\Users\nehem\.gradle'; .\gradlew.bat assembleDebug --no-daemon
```

## Architecture Summary

Chevere AI is an Android app using Clean Architecture, MVI, Room, Hilt, LiteRT-LM, ONNX Runtime Android, and WorkManager. The package root is `com.neo.chevere`.

### Main Areas

- `:app`: application code.
- `:ui-designsystem`: shared theme/components.
- `domain/`: contracts, shared models, explicit prompt policy, model/runtime enums.
- `data/agent/`: tool registry and Reason-Act-Observe orchestration.
- `data/inference/`: LiteRT-LM chat and local image-generation runtimes.
- `data/download/`: WorkManager download orchestration.
- `data/datasource/local/`: Room registry for installed models.
- `data/datasource/`: Firestore and Hugging Face model catalogs. Kaggle discovery was removed.
- `ui/chat/`: chat screen, capability-focused top bar, multimodal attachments, slash commands, image-model download prompt, age verification, explicit image masking, response sharing.
- `ui/marketplace/`: grouped chat/image model discovery, download, selection, and deletion UI.

## Current Model Runtime Behavior

- Text chat uses LiteRT-LM (`.litertlm` preferred).
- Image generation can be invoked by:
  - Gemma through `generate_image`, where prompts should be improved first.
  - Slash commands `/image`, `/img`, `/imagine`, which bypass Gemma.
- If no healthy image-generation model is installed, image requests should show a download prompt rather than letting the agent/tool loop fail.
- Chat models and image models are separate capabilities. Selecting/activating a chat model should not be confused with making an image model available.
- Attached images route through chat/vision inference, not image generation. Image-only sends should use `Describe this image.` as the prompt.
- ONNX diffusion bundles must be extracted directories with:
  - `text_encoder/model.ort`
  - `tokenizer/vocab.json`
  - `tokenizer/merges.txt`
  - `unet/model.ort`
  - `vae_decoder/model.ort`
- Qualcomm/QNN bundles are detected and validated, but native execution is not implemented.

## Explicit Image Flow

- Explicit image generation is debug-only. Release builds block explicit prompts before model execution.
- In debug builds, explicit image prompts trigger an age-verification dialog.
- Users under 18 are blocked from age-restricted image content.
- Verified explicit image generations continue through the image backend.
- Explicit generated images are masked by default in the chat UI.
- Users can reveal or hide each masked image using the visibility toggle.
- Assistant responses can be shared through the Android share sheet. Do not add report/flag UI until the app has a reporting mechanism.

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
- Do not add AI attribution or `Co-Authored-By` lines to commits.
